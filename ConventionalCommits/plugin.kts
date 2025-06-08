import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.InvokeAfterUpdateMode
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog
import com.intellij.openapi.vcs.checkin.CheckinEnvironment
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.ui.content.ContentFactory
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.DiffManager
import com.intellij.diff.DiffContentFactory
import com.intellij.icons.AllIcons

import java.awt.BorderLayout
import java.awt.Color
import java.awt.GridLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.*

import liveplugin.*


class ModifiedFilesPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val panel = JPanel()
    private val scrollPane = JScrollPane(panel)

    init {
        layout = BorderLayout()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        add(scrollPane, BorderLayout.CENTER)

        ApplicationManager.getApplication().messageBus.connect().subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun before(events: List<VFileEvent>) {
                    // dont think we need this yet. I'll leave it blank for now
                }

                override fun after(events: List<VFileEvent>) {
                    refreshFileList()
                }
            }
        )

        refreshFileList()
    }

    private fun refreshFileList() {
        val changeListManager = ChangeListManager.getInstance(project)

        changeListManager.invokeAfterUpdate({
            SwingUtilities.invokeLater {
                panel.removeAll()

                val modifiedFiles = getModifiedFiles()
                for (file in modifiedFiles) {
                    val checkBox = JCheckBox(file.name)
                    checkBox.toolTipText = file.path

                    checkBox.addMouseListener(object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent) {
                            if (e.clickCount == 2) {
                                openFileInEditor(project, file)
                            }
                        }
                    })

                    panel.add(checkBox)
                }

                panel.revalidate()
                panel.repaint()
            }
        }, InvokeAfterUpdateMode.BACKGROUND_CANCELLABLE, "FoxJ VCS Refresh", ModalityState.NON_MODAL)
    }

    private fun getModifiedFiles(): List<VirtualFile> {
        val changeListManager = ChangeListManager.getInstance(project)
        return changeListManager.allChanges.mapNotNull { it.virtualFile }
    }

    private fun openFileInEditor(project: Project, file: VirtualFile) {
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    private fun openDiffForFile(project: Project, file: VirtualFile) {
        // @todo: show a diff modal
    }
}

class ConventionalCommitsPanel(private val project: Project, private val settings: SettingsPanel)
    : JPanel(BorderLayout()) {

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

        if (!canPerformCommit(changes)) {
            return
        }

        ApplicationManager.getApplication().invokeLater {
            if (settings.useCommitDialog()) {
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

    private fun generateScopeText(): String {
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

    private fun commitWithMessage(project: Project, message: String) {
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

class SettingsPanel(private val project: Project) : JPanel(BorderLayout()) {
    val buttonPanel = JPanel(GridLayout(5, 2, 10, 10))
    val chkUseCommitDialog = JCheckBox("Use Commit Dialog")
    init {
        buttonPanel.add(chkUseCommitDialog)
        add(buttonPanel, BorderLayout.SOUTH)
    }

    fun useCommitDialog(): Boolean {
        return chkUseCommitDialog.isSelected
    }
}

project?.let { currentProject ->
    val disposable = Disposer.newDisposable("foxJCommitsPanel")
    Disposer.register(pluginDisposable, disposable)

    val settingsPanel = SettingsPanel(currentProject)
    val conventionalCommitsPanel = ConventionalCommitsPanel(currentProject, settingsPanel)
    val filesPanel = ModifiedFilesPanel(currentProject)

    val placeholderPanel = JPanel()
    val toolWindow = currentProject.registerToolWindow(
        toolWindowId = "FoxJ",
        component = placeholderPanel,
        disposable = disposable,
        anchor = ToolWindowAnchor.RIGHT
    )

    toolWindow.setIcon(AllIcons.Vcs.CommitNode);

    val contentManager = toolWindow.contentManager
    val contentFactory = ContentFactory.SERVICE.getInstance()

    val ccContent = contentFactory.createContent(conventionalCommitsPanel, "Commit", false)
    val filesContent = contentFactory.createContent(filesPanel, "Files", false)
    val settingsContent = contentFactory.createContent(settingsPanel, "Settings", false)

    ccContent.isCloseable = false
    filesContent.isCloseable = false
    settingsContent.isCloseable = false

    contentManager.removeAllContents(true)
    contentManager.addContent(ccContent)
    contentManager.addContent(filesContent)
    contentManager.addContent(settingsContent)

    Disposer.register(disposable, Disposable {
        show("🔻 Conventional Commits plugin stopped.")
    })
}
