package org.osate.propertiescodegen

import org.eclipse.core.commands.AbstractHandler
import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Path
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.SubMonitor
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.ui.actions.WorkspaceModifyOperation
import org.eclipse.ui.dialogs.ContainerGenerator
import org.eclipse.ui.handlers.HandlerUtil
import org.eclipse.ui.statushandlers.StatusManager
import org.osate.aadl2.EnumerationType
import org.osate.aadl2.PropertySet
import org.osate.aadl2.UnitsType
import org.osate.aadl2.modelsupport.resources.OsateResourceUtil
import java.lang.reflect.InvocationTargetException

class PropertiesCodeGenHandler : AbstractHandler() {
	override fun execute(event: ExecutionEvent?): Any? {
		val file = HandlerUtil.getCurrentStructuredSelection(event).firstElement as IFile
		val markers = file.findMarkers(null, true, IResource.DEPTH_ONE)
		if (!markers.any { it.getAttribute(IMarker.SEVERITY) == IMarker.SEVERITY_ERROR }) {
			val resource = ResourceSetImpl().getResource(OsateResourceUtil.toResourceURI(file), true)
			val propertySet = resource.contents.first() as PropertySet
			val javaFiles = generateJava(propertySet)
			val operation = object : WorkspaceModifyOperation() {
				override fun execute(monitor: IProgressMonitor?) {
					val subMonitor = SubMonitor.convert(
						monitor,
						"Generating Java Types",
						javaFiles.size * 2 + 1
					)
					val folderPath = file.project.fullPath.append("src-gen/${propertySet.name.toLowerCase()}")
					val folder = ContainerGenerator(folderPath).generateContainer(subMonitor.split(1))
					subMonitor.setWorkRemaining(folder.members().size + javaFiles.size)
					folder.members().forEach { it.delete(false, subMonitor.split(1)) }
					javaFiles.forEach { (fileName, contents) ->
						folder.getFile(Path(fileName))
							.create(contents.byteInputStream(), false, subMonitor.split(1))
					}
				}
			}
			try {
				HandlerUtil.getActiveWorkbenchWindow(event).run(true, true, operation)
			} catch (e: InvocationTargetException) {
				val status = Status(
					IStatus.ERROR,
					Activator.PLUGIN_ID,
					"Error while generating Java Types.",
					e.targetException
				)
				StatusManager.getManager().handle(status, StatusManager.LOG or StatusManager.SHOW)
			}
		} else {
			MessageDialog.openError(
				HandlerUtil.getActiveShell(event),
				"Errors in Property Set",
				"Cannot generate Java types for \"${file.name}\" because it has errors."
			)
		}
		return null
	}
}

data class GeneratedJava(val fileName: String, val contents: String)

fun generateJava(propertySet: PropertySet): List<GeneratedJava> {
	return propertySet.ownedPropertyTypes
		.filterIsInstance<EnumerationType>()
		.filter { it !is UnitsType }
		.map { enumType ->
			val typeName = enumType.name
				.split("_")
				.joinToString("") { it.toLowerCase().capitalize() }
			val literals = enumType.ownedLiterals.joinToString(",\n\t\t\t\t\t") { it.name.toUpperCase() }
			val contents = """
				package ${propertySet.name.toLowerCase()};
				
				import org.osate.aadl2.AbstractNamedValue;
				import org.osate.aadl2.EnumerationLiteral;
				import org.osate.aadl2.NamedValue;
				import org.osate.aadl2.Property;
				import org.osate.aadl2.PropertyConstant;
				import org.osate.aadl2.PropertyExpression;
				
				public enum $typeName {
					$literals;
					
					public static $typeName valueOf(PropertyExpression propertyExpression) {
						AbstractNamedValue abstractNamedValue = ((NamedValue) propertyExpression).getNamedValue();
						if (abstractNamedValue instanceof EnumerationLiteral) {
							return valueOf(((EnumerationLiteral) abstractNamedValue).getName().toUpperCase());
						} else if (abstractNamedValue instanceof Property) {
							throw new IllegalArgumentException("Reference to property not supported");
						} else if (abstractNamedValue instanceof PropertyConstant) {
							throw new IllegalArgumentException("Reference to property constant not supported");
						} else {
							throw new AssertionError("Unexpected type: " + abstractNamedValue.getClass().getName());
						}
					}
				}
			""".trimIndent()
			GeneratedJava("$typeName.java", contents)
		}
}