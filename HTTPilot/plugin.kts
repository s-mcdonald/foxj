import java.awt.BorderLayout
import java.awt.GridLayout
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import javax.swing.*

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.ui.content.ContentFactory

import liveplugin.*

class RequestPilotPanel(
    private val project: Project,
    private val settings: SettingsAuthPanel
) : JPanel(BorderLayout()) {

    private val methodDropdown = JComboBox(arrayOf("GET", "POST", "PUT", "DELETE", "PATCH"))
    private val urlField = JTextField("https://jsonplaceholder.typicode.com/todos/1")
    private val sendButton = JButton("Send Request")
    private val headersArea = JTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        isEditable = false
        border = BorderFactory.createTitledBorder("Response Headers")
    }
    private val responseArea = JTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        isEditable = false
    }

    init {
        val topPanel = JPanel(BorderLayout(5, 5)).apply {
            add(methodDropdown, BorderLayout.WEST)
            add(urlField, BorderLayout.CENTER)
            add(sendButton, BorderLayout.EAST)
        }

        val tabbedPane = JTabbedPane().apply {
            addTab("Headers", JScrollPane(headersArea))
            addTab("Body", JScrollPane(responseArea))
        }

        add(topPanel, BorderLayout.NORTH)
        add(tabbedPane, BorderLayout.CENTER)

        sendButton.addActionListener {
            val urlText = urlField.text.trim()
            if (urlText.isNotEmpty()) {
                performRequest(urlText)
            } else {
                Messages.showErrorDialog(project, "Please enter a valid URL.", "Invalid Input")
            }
        }
    }

    fun getHttpMethod(): String = methodDropdown.selectedItem.toString()

    private fun performRequest(urlText: String) {
        responseArea.text = "Sending request..."
        headersArea.text = ""

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val bearerToken = settings.getBearerToken()
                val username = settings.getUsername()
                val password = settings.getPassword()
                val method = getHttpMethod()

                val url = URL(urlText)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = method
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                if (bearerToken.isNotEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer $bearerToken")
                } else if (username.isNotEmpty() && password.isNotEmpty()) {
                    val auth = "$username:$password"
                    val encoded = Base64.getEncoder().encodeToString(auth.toByteArray())
                    conn.setRequestProperty("Authorization", "Basic $encoded")
                }

                val responseHeaders = conn.headerFields
                    .filterKeys { it != null }
                    .entries
                    .joinToString("\n") { "${it.key}: ${it.value.joinToString()}" }

                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()

                SwingUtilities.invokeLater {
                    headersArea.text = responseHeaders
                    responseArea.text = response
                }

            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    headersArea.text = ""
                    responseArea.text = "❌ Error: ${e.message}"
                }
            }
        }
    }
}

class SettingsAuthPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val formPanel = JPanel(GridLayout(5, 2, 10, 10))

    private val usernameField = JTextField()
    private val passwordField = JPasswordField()
    private val bearerTokenField = JTextField()

    init {
        formPanel.border = BorderFactory.createTitledBorder("Authentication Settings")

        formPanel.add(JLabel("Username:"))
        formPanel.add(usernameField)

        formPanel.add(JLabel("Password:"))
        formPanel.add(passwordField)

        formPanel.add(JLabel("Bearer Token:"))
        formPanel.add(bearerTokenField)

        add(formPanel, BorderLayout.NORTH)
    }

    fun getUsername(): String = usernameField.text.trim()

    fun getPassword(): String = String(passwordField.password).trim()

    fun getBearerToken(): String = bearerTokenField.text.trim()
}

project?.let { currentProject ->
    val disposable = Disposer.newDisposable("foxJRequestPanel")
    Disposer.register(pluginDisposable, disposable)

    val settingsAuthPanel = SettingsAuthPanel(currentProject)
    val requestPilotPanel = RequestPilotPanel(currentProject, settingsAuthPanel)

    val placeholderPanel = JPanel()
    val toolWindow = currentProject.registerToolWindow(
        toolWindowId = "HTTPilot",
        component = placeholderPanel,
        disposable = disposable,
        anchor = ToolWindowAnchor.RIGHT
    )

    toolWindow.setIcon(AllIcons.Actions.StepOutCodeBlock);

    val contentManager = toolWindow.contentManager
    val contentFactory = ContentFactory.SERVICE.getInstance()

    val ccContent = contentFactory.createContent(requestPilotPanel, "Request", false)
    val settingsContent = contentFactory.createContent(settingsAuthPanel, "Settings", false)

    ccContent.isCloseable = false
    settingsContent.isCloseable = false

    contentManager.removeAllContents(true)
    contentManager.addContent(ccContent)
    contentManager.addContent(settingsContent)

    Disposer.register(disposable, Disposable {
        show("🔻 RequestPilot plugin stopped.")
    })
}
