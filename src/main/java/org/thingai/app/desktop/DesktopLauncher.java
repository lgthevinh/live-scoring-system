package org.thingai.app.desktop;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Swing control window for the scoring server. Runs its own jar as a child process in {@code ~/ScoringSystem},
 * mirrors its output to {@code ~/ScoringSystem/logs/}, and shows the server status, URL, data folder and live log.
 */
public class DesktopLauncher {

    private enum State { STARTING, RUNNING, ERROR, STOPPED }

    private static final Pattern URL_LINE = Pattern.compile("Service running on URL:\\s*(https?://\\S+)");
    private static final Pattern APP_DIR_LINE = Pattern.compile("with app directory:\\s*(.+)$");
    private static final int LOG_MAX_LINES = 400;
    private static final int LOG_FLUSH_INTERVAL_MS = 60;
    private static final int READINESS_TIMEOUT_MS = 45_000;

    private static final Color AMBER = new Color(230, 160, 30);
    private static final Color GREEN = new Color(40, 170, 90);
    private static final Color RED = new Color(215, 65, 65);
    private static final Color GRAY = new Color(140, 140, 150);

    private JFrame frame;
    private JLabel statusDot;
    private JLabel statusText;
    private JLabel urlLabel;
    private JProgressBar progress;
    private JButton openBtn;
    private JButton copyBtn;
    private JLabel dataPathLabel;
    private JButton dataOpenBtn;
    private JLabel logPathLabel;
    private JButton logsToggleBtn;
    private JComponent logsView;
    private JTextArea logArea;
    private JButton themeBtn;

    private Process serverProcess;
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private boolean darkMode = false;

    private File workDir;
    private File logsDir;
    private File bootJar;
    private volatile File logFile;
    private volatile File dataDir;
    private volatile String serverUrl;

    private final List<String> pendingLog = new ArrayList<>();
    private Timer logFlushTimer;

    public static void main(String[] args) {
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(() -> {
            DesktopLauncher launcher = new DesktopLauncher();
            launcher.createAndShow();
            launcher.startServerAsync();
        });
    }

    // =====================================================================
    // UI
    // =====================================================================

    private void createAndShow() {
        resolveLayoutPaths();

        frame = new JFrame("Live Scoring System");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { onExit(); }
        });

        JComponent content = buildContent();
        installKeyBindings(content);
        frame.setContentPane(content);
        frame.pack();
        frame.setMinimumSize(new Dimension(560, frame.getHeight()));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        logFlushTimer = new Timer(LOG_FLUSH_INTERVAL_MS, e -> flushLog());
        logFlushTimer.start();

        setState(State.STARTING, "Starting server\u2026");
    }

    private JComponent buildContent() {
        JPanel root = new JPanel(new BorderLayout());
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(section(buildHeader(), false));
        top.add(section(buildHero(), true));
        top.add(section(buildDetails(), true));
        top.add(section(buildLogsToggle(), true));
        root.add(top, BorderLayout.NORTH);

        logsView = buildLogsView();
        logsView.setVisible(false);
        root.add(logsView, BorderLayout.CENTER);
        return root;
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout());

        JLabel title = new JLabel("Live Scoring System");
        title.putClientProperty("FlatLaf.styleClass", "h3");
        header.add(title, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.add(mutedLabel(version()));
        right.add(Box.createHorizontalStrut(12));
        themeBtn = borderlessButton("Dark", e -> toggleTheme());
        right.add(themeBtn);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JComponent buildHero() {
        JPanel hero = new JPanel();
        hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
        hero.setBorder(new EmptyBorder(8, 0, 8, 0));

        statusDot = new JLabel("\u25CF");
        statusDot.putClientProperty("FlatLaf.styleClass", "h4");
        statusText = new JLabel();
        statusText.putClientProperty("FlatLaf.styleClass", "h4");
        hero.add(row(statusDot, statusText));
        hero.add(Box.createVerticalStrut(10));

        urlLabel = new JLabel("Waiting for server\u2026");
        urlLabel.putClientProperty("FlatLaf.styleClass", "h2");
        copyBtn = linkButton("Copy", e -> copyUrl());
        JPanel urlRow = new JPanel(new BorderLayout(12, 0));
        urlRow.add(urlLabel, BorderLayout.CENTER);
        urlRow.add(copyBtn, BorderLayout.EAST);
        hero.add(alignLeft(urlRow));
        hero.add(Box.createVerticalStrut(10));

        progress = new JProgressBar();
        progress.setIndeterminate(true);
        progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 4));
        hero.add(alignLeft(progress));
        hero.add(Box.createVerticalStrut(16));

        openBtn = new JButton("Open in Browser");
        openBtn.putClientProperty("FlatLaf.style", "background: $Component.accentColor; foreground: #fff;"
                + " borderColor: $Component.accentColor; hoverBackground: darken($Component.accentColor,6%);"
                + " pressedBackground: darken($Component.accentColor,12%)");
        openBtn.addActionListener(e -> openInBrowser());
        JButton exitBtn = new JButton("Stop & Exit");
        exitBtn.addActionListener(e -> onExit());
        hero.add(row(openBtn, exitBtn));
        return hero;
    }

    private JComponent buildDetails() {
        JPanel details = new JPanel(new GridBagLayout());
        dataPathLabel = new JLabel("Waiting for server\u2026");
        dataOpenBtn = linkButton("Open", e -> openFolder(dataDir));
        dataOpenBtn.setEnabled(false);
        logPathLabel = new JLabel("Pending\u2026");
        JButton logsOpenBtn = linkButton("Open", e -> openFolder(logsDir));
        addDetailRow(details, 0, "Data", dataPathLabel, dataOpenBtn);
        addDetailRow(details, 1, "Logs", logPathLabel, logsOpenBtn);
        return details;
    }

    private JComponent buildLogsToggle() {
        logsToggleBtn = borderlessButton("\u25B8  Server logs", e -> toggleLogs());
        logsToggleBtn.setHorizontalAlignment(SwingConstants.LEFT);
        return alignLeft(logsToggleBtn);
    }

    private JComponent buildLogsView() {
        logArea = new JTextArea(14, 0);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setMargin(new Insets(8, 12, 8, 12));
        JScrollPane scroll = new JScrollPane(logArea);
        // Zero preferred width so long lines scroll horizontally instead of widening the window
        scroll.setPreferredSize(new Dimension(0, scroll.getPreferredSize().height));
        scroll.setBorder(new DividerBorder());
        return scroll;
    }

    /** Wraps a block with page padding and an optional top divider. */
    private JComponent section(JComponent body, boolean divider) {
        JPanel p = new JPanel(new BorderLayout());
        EmptyBorder padding = new EmptyBorder(12, 20, 12, 20);
        p.setBorder(divider ? BorderFactory.createCompoundBorder(new DividerBorder(), padding) : padding);
        p.add(body);
        return p;
    }

    private void addDetailRow(JPanel parent, int y, String name, JLabel value, JButton action) {
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridy = y;
        gc.anchor = GridBagConstraints.WEST;
        gc.insets = new Insets(2, 0, 2, 16);
        parent.add(mutedLabel(name), gc);
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        // Let long paths truncate with "..." instead of widening the window
        value.setPreferredSize(new Dimension(0, value.getPreferredSize().height));
        parent.add(value, gc);
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        gc.insets = new Insets(2, 0, 2, 0);
        parent.add(action, gc);
    }

    private static JPanel row(JComponent... items) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        for (int i = 0; i < items.length; i++) {
            if (i > 0) p.add(Box.createHorizontalStrut(8));
            p.add(items[i]);
        }
        return alignLeft(p);
    }

    private static <T extends JComponent> T alignLeft(T c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private static JLabel mutedLabel(String text) {
        JLabel l = new JLabel(text);
        l.putClientProperty("FlatLaf.style", "foreground: $Label.disabledForeground");
        return l;
    }

    /** Text-only button with no side padding, so it lines up with surrounding labels. */
    private static JButton borderlessButton(String text, java.awt.event.ActionListener action) {
        JButton b = new JButton(text);
        b.putClientProperty("JButton.buttonType", "borderless");
        b.putClientProperty("FlatLaf.style", "margin: 2,0,2,0");
        b.setFocusable(false);
        b.addActionListener(action);
        return b;
    }

    private static JButton linkButton(String text, java.awt.event.ActionListener action) {
        JButton b = borderlessButton(text, action);
        b.putClientProperty("FlatLaf.style", "margin: 2,0,2,0; foreground: $Component.linkColor");
        return b;
    }

    private void installKeyBindings(JComponent root) {
        int mod = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        bindKey(root, KeyStroke.getKeyStroke(KeyEvent.VK_O, mod), this::openInBrowser);
        bindKey(root, KeyStroke.getKeyStroke(KeyEvent.VK_L, mod), this::toggleLogs);
        bindKey(root, KeyStroke.getKeyStroke(KeyEvent.VK_C, mod | KeyEvent.SHIFT_DOWN_MASK), this::copyUrl);
        bindKey(root, KeyStroke.getKeyStroke(KeyEvent.VK_Q, mod), this::onExit);
    }

    private static void bindKey(JComponent root, KeyStroke key, Runnable action) {
        String name = key.toString();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(key, name);
        root.getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }

    // =====================================================================
    // State
    // =====================================================================

    private void setState(State s, String message) {
        SwingUtilities.invokeLater(() -> {
            boolean running = s == State.RUNNING;
            statusDot.setForeground(s == State.STARTING ? AMBER : running ? GREEN : s == State.ERROR ? RED : GRAY);
            statusText.setText(message);
            progress.setVisible(s == State.STARTING);
            openBtn.setEnabled(running);
            copyBtn.setEnabled(running);
            if (s == State.ERROR && !logsView.isVisible()) toggleLogs();
        });
    }

    // =====================================================================
    // Server lifecycle
    // =====================================================================

    private void startServerAsync() {
        new Thread(this::startServer, "server-starter").start();
    }

    private void startServer() {
        try {
            if (!bootJar.isFile()) {
                setState(State.ERROR, "Server jar not found: " + bootJar.getAbsolutePath());
                return;
            }
            Files.createDirectories(logsDir.toPath());
            String ts = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            logFile = new File(logsDir, "scoring-" + ts + ".log");
            SwingUtilities.invokeLater(() -> setPath(logPathLabel, logFile));

            // Run the server on the runtime that runs this launcher (the jpackage-bundled one when installed).
            // The working directory is the user's writable folder; MatchMaker ships next to the jar.
            String javaBin = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString();
            ProcessBuilder pb = new ProcessBuilder(javaBin,
                    "-Dmatchmaker.dir=" + bootJar.getParentFile().getAbsolutePath(),
                    "-jar", bootJar.getAbsolutePath());
            pb.directory(workDir);
            pb.redirectErrorStream(true);
            serverProcess = pb.start();
            // Don't leave an orphaned server if the launcher is killed (e.g. Ctrl+C in the terminal)
            Runtime.getRuntime().addShutdownHook(new Thread(serverProcess::destroy));
            startOutputPump();

            serverProcess.onExit().thenAccept(p -> {
                if (stopping.get()) return;
                setState(State.ERROR, "Server exited unexpectedly (code " + p.exitValue() + ")");
            });
            new Thread(this::awaitReadiness, "readiness-probe").start();
        } catch (Exception ex) {
            setState(State.ERROR, "Failed to start server: " + ex.getMessage());
            appendLog("[launcher] " + ex);
        }
    }

    /** Copies server output to the log file and the log view, and picks up the URL and data folder. */
    private void startOutputPump() {
        Thread pump = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(serverProcess.getInputStream(), StandardCharsets.UTF_8));
                 PrintStream fileOut = new PrintStream(new FileOutputStream(logFile, true), true, StandardCharsets.UTF_8)) {
                String line;
                while ((line = r.readLine()) != null) {
                    fileOut.println(line);
                    appendLog(line);
                    handleServerLine(line);
                }
            } catch (IOException e) {
                if (!stopping.get()) appendLog("[launcher] output error: " + e.getMessage());
            }
        }, "output-pump");
        pump.setDaemon(true);
        pump.start();
    }

    private void handleServerLine(String line) {
        Matcher url = URL_LINE.matcher(line);
        if (serverUrl == null && url.find()) {
            serverUrl = url.group(1);
            SwingUtilities.invokeLater(() -> urlLabel.setText(serverUrl));
            return;
        }
        Matcher dir = APP_DIR_LINE.matcher(line);
        if (dataDir == null && dir.find()) {
            dataDir = new File(dir.group(1).trim());
            SwingUtilities.invokeLater(() -> {
                setPath(dataPathLabel, dataDir);
                dataOpenBtn.setEnabled(true);
            });
        }
    }

    private void awaitReadiness() {
        long deadline = System.currentTimeMillis() + READINESS_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (stopping.get() || !serverProcess.isAlive()) return;
            URI uri = serverUrl == null ? null : URI.create(serverUrl);
            if (uri != null && uri.getHost() != null && canConnect(uri.getHost(), port(uri))) {
                setState(State.RUNNING, "Running");
                return;
            }
            sleep(400);
        }
        if (!stopping.get()) setState(State.ERROR, "Server did not start within " + READINESS_TIMEOUT_MS / 1000 + "s");
    }

    private void onExit() {
        if (!stopping.compareAndSet(false, true)) return;
        setState(State.STOPPED, "Stopping server\u2026");
        new Thread(() -> {
            try {
                if (serverProcess != null && serverProcess.isAlive()) {
                    serverProcess.destroy();
                    if (!serverProcess.waitFor(5, TimeUnit.SECONDS)) serverProcess.destroyForcibly();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            System.exit(0);
        }, "shutdown").start();
    }

    // =====================================================================
    // Actions
    // =====================================================================

    private void appendLog(String line) {
        synchronized (pendingLog) {
            pendingLog.add(line);
        }
    }

    private void flushLog() {
        String text;
        synchronized (pendingLog) {
            if (pendingLog.isEmpty()) return;
            text = String.join("\n", pendingLog) + "\n";
            pendingLog.clear();
        }
        logArea.append(text);
        int over = logArea.getLineCount() - LOG_MAX_LINES;
        if (over > 0) {
            try {
                logArea.replaceRange("", 0, logArea.getLineEndOffset(over - 1));
            } catch (Exception ignored) {
            }
        }
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void toggleLogs() {
        boolean show = !logsView.isVisible();
        logsView.setVisible(show);
        logsToggleBtn.setText((show ? "\u25BE" : "\u25B8") + "  Server logs");
        frame.setSize(frame.getWidth(), frame.getPreferredSize().height);
    }

    private void toggleTheme() {
        darkMode = !darkMode;
        if (darkMode) FlatDarkLaf.setup(); else FlatLightLaf.setup();
        FlatLaf.updateUI();
        themeBtn.setText(darkMode ? "Light" : "Dark");
    }

    private void copyUrl() {
        if (serverUrl == null) return;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(serverUrl), null);
        copyBtn.setText("Copied");
        Timer t = new Timer(1500, e -> copyBtn.setText("Copy"));
        t.setRepeats(false);
        t.start();
    }

    private void openInBrowser() {
        if (serverUrl == null) return;
        try {
            Desktop.getDesktop().browse(new URI(serverUrl));
        } catch (Exception ex) {
            showError("Could not open the browser: " + ex.getMessage());
        }
    }

    private void openFolder(File dir) {
        if (dir == null || !dir.exists()) return;
        try {
            Desktop.getDesktop().open(dir);
        } catch (Exception ex) {
            showError("Could not open " + dir + ": " + ex.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(frame, message, "Live Scoring System", JOptionPane.ERROR_MESSAGE);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private void resolveLayoutPaths() {
        workDir = new File(System.getProperty("user.home"), "ScoringSystem");
        logsDir = new File(workDir, "logs");
        // The launcher and the server ship in the same jar, so the server jar is this class's own jar
        try {
            bootJar = new File(DesktopLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            bootJar = new File("scoring-system.jar");
        }
    }

    /** Shows a path with the home directory shortened to "~", full path in the tooltip. */
    private static void setPath(JLabel label, File f) {
        String full = f.getAbsolutePath();
        String home = System.getProperty("user.home");
        label.setText(full.startsWith(home) ? "~" + full.substring(home.length()) : full);
        label.setToolTipText(full);
    }

    private static int port(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static boolean canConnect(String host, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 400);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private static String version() {
        String v = DesktopLauncher.class.getPackage().getImplementationVersion();
        return v != null ? "v" + v : "dev";
    }

    /** One-pixel top divider drawn in the current theme's separator color. */
    private static final class DividerBorder extends AbstractBorder {
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            g.setColor(UIManager.getColor("Separator.foreground"));
            g.fillRect(x, y, width, 1);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(1, 0, 0, 0);
            return insets;
        }
    }
}
