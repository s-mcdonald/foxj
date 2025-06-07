import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.ui.content.ContentFactory
import liveplugin.*
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.*

class ConventionalCommitsPanel(private val project: Project) : JPanel(BorderLayout()) {

    val commitTypes = arrayOf(
        "feat",
        "fix",
        "chore",
        "style",
        "refactor",
        "revert",
        "test",
        "perf",
        "build",
        "ci",
        "docs",
    )

    private val scopeList = mutableListOf(
        "",
        "component",
        "backend",
        "frontend",
        "evaluator",
    )

    val buttonPanel = JPanel(GridLayout(2, 2, 2, 2))

    val btnCommit = JButton("Commit")

    val typeComboBox = JComboBox(commitTypes)
    private val scopeComboBox = JComboBox(scopeList.toTypedArray())

    val importantCheckbox = JCheckBox("Important")
    val txtProjectKey = JTextField("", 20)

    private val textArea = JTextArea(5, 20).apply {
        lineWrap = true
        wrapStyleWord = true
        background = UIManager.getColor("EditorPane.background")
        foreground = UIManager.getColor("TextArea.foreground")
        caretColor = UIManager.getColor("TextArea.caretForeground")
    }

    init {
        scopeComboBox.isEditable = true
        btnCommit.addActionListener {
            commitSelectedChanges()
        }

        buttonPanel.add(typeComboBox)
        buttonPanel.add(scopeComboBox)

        buttonPanel.add(btnCommit)
        buttonPanel.add(importantCheckbox)

        add(JScrollPane(textArea), BorderLayout.CENTER)
        add(buttonPanel, BorderLayout.SOUTH)
    }

    private fun commitSelectedChanges() {
        ApplicationManager.getApplication().invokeLater {
            val changeListManager = ChangeListManager.getInstance(project)
            val changes = changeListManager.defaultChangeList.changes.toList()

            if (changes.isEmpty()) {
                show("FoxJ: Nothing to commit")
            } else if (textArea.text.trim().isBlank()) {
                Messages.showInfoMessage(project, "Please enter a commit message", "FoxJ: Commit")
            } else {
                val input = scopeComboBox.editor.item.toString().trim()
                if (input.isNotEmpty() && !scopeList.contains(input)) {
                    scopeList.add(0, input)
                    scopeComboBox.insertItemAt(input, 1)
                }

                // @todo: we prob should clean up this format/generation later
                CommitChangeListDialog.commitChanges(
                    project,
                    changes,
                    changeListManager.defaultChangeList,
                    null,
                    typeComboBox.selectedItem.toString() +
                    (importantCheckbox.isSelected).let { if (it) "!" else "" } +
                    ": " + textArea.text.trim()
                )
            }
        }
    }

    override fun addNotify() {
        super.addNotify()
        rootPane?.defaultButton = btnCommit
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

    ccContent.isCloseable = false

    contentManager.removeAllContents(true)
    contentManager.addContent(ccContent)
    contentManager.addContent(filesContent)
    contentManager.addContent(utilContent)

    Disposer.register(disposable, Disposable {
        println("🔻 Conventional Commits plugin stopped.")
    })
}
