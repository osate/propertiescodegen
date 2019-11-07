package org.osate.propertiescodegen

import org.eclipse.core.commands.AbstractHandler
import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.core.resources.IFile
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import org.eclipse.ui.handlers.HandlerUtil
import org.osate.aadl2.PropertySet
import org.osate.aadl2.modelsupport.resources.OsateResourceUtil

class PropertiesCodeGenHandler : AbstractHandler() {
	override fun execute(event: ExecutionEvent): Any? {
		val file = HandlerUtil.getCurrentStructuredSelection(event).firstElement as IFile
		val resource = ResourceSetImpl().getResource(OsateResourceUtil.toResourceURI(file), true)
		val propertySet = resource.contents.first() as PropertySet
		println("CodeGen called on ${propertySet.name}")
		return null
	}
}