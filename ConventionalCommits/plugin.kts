import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog
import com.intellij.openapi.vcs.checkin.CheckinEnvironment
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.ui.content.ContentFactory
import com.intellij.openapi.vcs.changes.Change

import java.awt.BorderLayout
import java.awt.Color
import java.awt.GridLayout
import javax.swing.*

import liveplugin.*

class ConventionalCommitsPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val commitTypes = arrayOf(
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

    private val scopeComboBox = JComboBox(scopeList.toTypedArray())

    val buttonPanel = JPanel(GridLayout(2, 2, 2, 2))
    val btnCommit = JButton("Commit")
    val typeComboBox = JComboBox(commitTypes)
    val importantCheckbox = JCheckBox("Important")
    val globalScheme = EditorColorsManager.getInstance().globalScheme

    private val textArea = JTextArea(5, 20).apply {
        lineWrap = true
        wrapStyleWord = true
        background = globalScheme.defaultBackground
        foreground = globalScheme.defaultForeground
        caretColor = globalScheme.getColor(EditorColors.CARET_COLOR) ?: Color.WHITE
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

        val changeListManager = ChangeListManager.getInstance(project)
        val changes = changeListManager.defaultChangeList.changes.toList()

        val commitMessage = generateScopeText()

        if (!canPerformCommit(changes))
        {
            return
        }

        ApplicationManager.getApplication().invokeLater {

            if (true) {
                // @todo: make a toggle so users can choose how they want this
                CommitChangeListDialog.commitChanges(
                    project,
                    changes,
                    changeListManager.defaultChangeList,
                    null,
                    commitMessage
                )
            } else {
                commitWithMessage(project, commitMessage)
            }
        }
    }

    private fun canPerformCommit(changes: List<Change>): Boolean {
        if (changes.isEmpty()) {
            show("FoxJ: Nothing to commit")
            return false
        }

        if (textArea.text.trim().isBlank()) {
            Messages.showInfoMessage(project, "Please enter a commit message", "FoxJ: Commit")
            return false
        }

        return true
    }

    private fun generateScopeText(): String
    {
        val input = scopeComboBox.editor.item.toString().trim()

        if (input.isNotEmpty() && !scopeList.contains(input)) {
            scopeList.add(0, input)
            scopeComboBox.insertItemAt(input, 1)
        }

        val scopeText = if (input.isNotEmpty()) "($input)" else ""

        return "${typeComboBox.selectedItem}$scopeText${if (importantCheckbox.isSelected) "!" else ""}: ${textArea.text.trim()}"
    }

    override fun addNotify() {
        super.addNotify()
        rootPane?.defaultButton = btnCommit
    }

    fun commitWithMessage(project: Project, message: String) {
        val changeListManager = ChangeListManager.getInstance(project)
        val defaultChangeList: LocalChangeList = changeListManager.defaultChangeList
        val changes = defaultChangeList.changes.toList()

        if (changes.isEmpty()) {
            show("No changes to commit")
            return
        }

        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        val vcs = vcsManager.findVcsByName("Git")
        val checkinEnv: CheckinEnvironment? = vcs?.checkinEnvironment

        if (checkinEnv != null) {

            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    show("Committing in background...")

                    val result = checkinEnv.commit(changes, message)

                    if (result.isNullOrEmpty()) {
                        show("Commit succeeded")
                    } else {
                        for (e in result) {
                            show("${e.message}")
                        }
                    }

                    VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
                } catch (e: Exception) {
                    show("Exception during commit: ${e.message}")
                }
            }
        } else {
            show("CheckinEnvironment not available for VCS: ${vcs?.name}")
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

    ccContent.isCloseable = false

    contentManager.removeAllContents(true)
    contentManager.addContent(ccContent)
    contentManager.addContent(filesContent)
    contentManager.addContent(utilContent)

    Disposer.register(disposable, Disposable {
        show("🔻 Conventional Commits plugin stopped.")
    })
}
