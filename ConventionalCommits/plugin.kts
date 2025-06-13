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
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Font

import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.SwingUtilities
import javax.swing.JComboBox
import javax.swing.JButton
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JScrollPane

import liveplugin.*

class ModifiedFilesPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val panel = JPanel()
    private val scrollPane = JScrollPane(panel)
    private val changeListManager = ChangeListManager.getInstance(project)

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
                                openDiffForFile(project, file)
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
        return changeListManager.allChanges.mapNotNull { it.virtualFile }
    }

    private fun openFileInEditor(project: Project, file: VirtualFile) {
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    private fun openDiffForFile(project: Project, file: VirtualFile) {

        val change = changeListManager.allChanges.firstOrNull {
            it.virtualFile == file
        }

        showDiffForChange(project, change)
    }

    private fun showDiffForChange(project: Project, change: Change?) {

        // @todo: review this method because i dont think we are getting the full experience here
        val beforeString = change?.beforeRevision?.content.orEmpty()
        val afterString = change?.afterRevision?.content.orEmpty()

        val beforeContent = DiffContentFactory.getInstance().create(project, beforeString)
        val afterContent = DiffContentFactory.getInstance().create(project, afterString)

        val title = "Diff: ${change?.virtualFile?.name ?: "File"}"

        val request = SimpleDiffRequest(
            title,
            beforeContent,
            afterContent,
            "Last Commit",
            "Working Tree"
        )

        DiffManager.getInstance().showDiff(project, request)
    }
}

class ConventionalCommitsPanel(private val project: Project, private val settings: SettingsPanel)
    : JPanel(BorderLayout()) {

    private val changeListManager = ChangeListManager.getInstance(project)
    private val globalEditorScheme = EditorColorsManager.getInstance().globalScheme
    private val arrayOfCommitTypes = arrayOf(
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

    private val panelButtonActions = JPanel(GridLayout(2, 2, 2, 2))

    private val compScopeWidget = JComboBox(scopeList.toTypedArray())
    private val compCommitButton = JButton("Commit")
    private val compCommitTypeCboBox = JComboBox(arrayOfCommitTypes)
    private val compImportantCheckbox = JCheckBox("Important")
    private val compCommitMessageTextArea = object : JTextField(20) {
        private val placeholder = "Enter commit message..."

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            if (text.isEmpty() && !hasFocus()) {
                val g2 = g.create() as Graphics2D
                g2.color = Color.GRAY
                g2.drawString(placeholder, insets.left + 2, height / 2 + font.size / 2 - 2)
                g2.dispose()
            }
        }
    }.apply {
        background = globalEditorScheme.defaultBackground
        foreground = globalEditorScheme.defaultForeground
        caretColor = globalEditorScheme.getColor(EditorColors.CARET_COLOR) ?: Color.WHITE
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        )
    }

    private val compCommitMessageBodyTextArea = object : JTextArea(5, 20) {
        private val placeholder = "Optional: commit body message..."

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            if (text.isEmpty() && !hasFocus()) {
                val g2 = g.create() as Graphics2D
                g2.color = Color.GRAY
                g2.drawString(placeholder, insets.left + 2, insets.top + font.size)
                g2.dispose()
            }
        }
    }.apply {
        lineWrap = true
        wrapStyleWord = true
        background = globalEditorScheme.defaultBackground
        foreground = globalEditorScheme.defaultForeground
        caretColor = globalEditorScheme.getColor(EditorColors.CARET_COLOR) ?: Color.WHITE
        border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
    }

    init {
        compScopeWidget.isEditable = true
        compCommitButton.addActionListener {
            commitSelectedChanges()
        }

        panelButtonActions.add(compCommitTypeCboBox)
        panelButtonActions.add(compScopeWidget)

        panelButtonActions.add(compCommitButton)
        panelButtonActions.add(compImportantCheckbox)

        add(JScrollPane(compCommitMessageTextArea), BorderLayout.NORTH)
        add(JScrollPane(compCommitMessageBodyTextArea), BorderLayout.CENTER)
        add(panelButtonActions, BorderLayout.SOUTH)
    }

    private fun commitSelectedChanges() {
        val changes = changeListManager.defaultChangeList.changes.toList()

        if (!canPerformCommit(changes)) {
            return
        }

        val baseMessage = generateScopeText()
        val commitMessage = if (compCommitMessageBodyTextArea.text.trim().isNotEmpty()) {
            baseMessage + "\n\n" + compCommitMessageBodyTextArea.text.trim()
        } else {
            baseMessage
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

        if (compCommitMessageTextArea.text.trim().isBlank()) {
            Messages.showInfoMessage(project, "Please enter a commit message", "FoxJ: Commit")
            return false
        }

        return true
    }

    private fun generateScopeText(): String {
        val input = compScopeWidget.editor.item.toString().trim()

        if (input.isNotEmpty() && !scopeList.contains(input)) {
            scopeList.add(0, input)
            compScopeWidget.insertItemAt(input, 1)
        }

        val scopeText = if (input.isNotEmpty()) "($input)" else ""

        return "${compCommitTypeCboBox.selectedItem}$scopeText${if (compImportantCheckbox.isSelected) "!" else ""}: ${compCommitMessageTextArea.text.trim()}"
    }

    override fun addNotify() {
        super.addNotify()
        rootPane?.defaultButton = compCommitButton
    }

    private fun commitWithMessage(project: Project, message: String) {
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

    toolWindow.setIcon(AllIcons.Actions.Lightning);

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
