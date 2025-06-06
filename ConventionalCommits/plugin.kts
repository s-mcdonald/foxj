import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.JPanel
import liveplugin.*

class ConventionalCommitsPanel(private val project: Project) : JPanel(BorderLayout()) {
    val buttonPanel = JPanel(GridLayout(2, 2, 2, 2))

    init {
        add(buttonPanel, BorderLayout.SOUTH)
    }

    private fun commitSelectedChanges() {
        ApplicationManager.getApplication().invokeLater {
            // ..
        }
    }
}

class FilesPanel(private val project: Project) : JPanel(BorderLayout()) {
    val buttonPanel = JPanel(GridLayout(5, 2, 10, 10))
    init {
        add(buttonPanel, BorderLayout.SOUTH)
    }
    private fun actionHandler() {
        // action here
    }
}

class UtilPanel(private val project: Project) : JPanel(BorderLayout()) {
    val buttonPanel = JPanel(GridLayout(5, 2, 10, 10))
    init {
        add(buttonPanel, BorderLayout.SOUTH)
    }
    private fun actionHandler() {
        // action here
    }
}

project?.let { currentProject ->
    val disposable = Disposer.newDisposable("foxJCommitsPanel")
    Disposer.register(pluginDisposable, disposable)

    val conventionalCommitsPanel = ConventionalCommitsPanel(currentProject)
    val utilPanel = UtilPanel(currentProject)
    val changedFilesPanel = FilesPanel(currentProject)

    val placeholderPanel = JPanel()
    val toolWindow = currentProject.registerToolWindow(
        toolWindowId = "FoxJ",
        component = placeholderPanel,
        disposable = disposable,
        anchor = ToolWindowAnchor.RIGHT
    )

    val contentManager = toolWindow.contentManager
    val contentFactory = ContentFactory.SERVICE.getInstance()

    val ccContent = contentFactory.createContent(conventionalCommitsPanel, "Commit", false)
    val filesContent = contentFactory.createContent(changedFilesPanel, "Files", false)
    val utilContent = contentFactory.createContent(utilPanel, "Utilities", false)

    contentManager.removeAllContents(true)
    contentManager.addContent(ccContent)
    contentManager.addContent(filesContent)
    contentManager.addContent(utilContent)

    Disposer.register(disposable, Disposable {
        println("🔻 Conventional Commits plugin stopped.")
    })
}
