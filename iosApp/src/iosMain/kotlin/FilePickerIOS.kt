import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerModeOpen
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.UIApplication
import platform.darwin.NSObject
import platform.UniformTypeIdentifiers.UTType

private fun topViewController(): UIViewController? {
    val app = UIApplication.sharedApplication
    val keyWindow = app.keyWindow
    var vc = keyWindow?.rootViewController
    while (vc?.presentedViewController != null) {
        vc = vc?.presentedViewController
    }
    return vc
}

private class PickerDelegate(
    private val onPicked: (NSURL?) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        onPicked(url)
    }
    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onPicked(null)
    }
}

object FilePickerIOS {
    private val retainedDelegates = mutableListOf<PickerDelegate>()

    fun pickFile(allowedTypes: List<String> = emptyList(), callback: (String?) -> Unit) {
        val contentTypes: List<*> = if (allowedTypes.isNotEmpty()) {
            allowedTypes.mapNotNull { ext ->
                when (ext.lowercase()) {
                    "zip" -> UTType.ZIPArchive
                    else -> UTType.Item
                }
            }
        } else listOf(UTType.Item)
        val picker = if (contentTypes.isNotEmpty())
            UIDocumentPickerViewController(forOpeningContentTypes = contentTypes)
        else UIDocumentPickerViewController(documentTypes = listOf("public.item"), inMode = UIDocumentPickerModeOpen)
        val delegate = PickerDelegate { url ->
            callback(url?.absoluteString)
            retainedDelegates.removeIf { it === delegate }
        }
        retainedDelegates.add(delegate)
        picker.delegate = delegate
        picker.allowsMultipleSelection = false
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }

    fun pickFolder(callback: (String?) -> Unit) {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTType.Folder))
        val delegate = PickerDelegate { url ->
            callback(url?.absoluteString)
            retainedDelegates.removeIf { it === delegate }
        }
        retainedDelegates.add(delegate)
        picker.delegate = delegate
        picker.allowsMultipleSelection = false
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}
