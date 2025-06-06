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

project?.let { currentProject ->
    val disposable = Disposer.newDisposable("foxJCommitsPanel")
    Disposer.register(pluginDisposable, disposable)

    val conventionalCommitsPanel = ConventionalCommitsPanel(currentProject)

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

    contentManager.removeAllContents(true)
    contentManager.addContent(ccContent)

    Disposer.register(disposable, Disposable {
        println("🔻 Conventional Commits plugin stopped.")
    })
}
