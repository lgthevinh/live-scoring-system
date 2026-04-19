package org.thingai.app.desktop;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Swing launcher for the scoring server. Starts the boot jar as a child
 * process, parses its stdout to discover the chosen port, mirrors the log
 * to a file, and surfaces a status panel + live log tail.
 *
 * <p>Port discovery strategy: the server logs
 * {@code Service running on URL: http://<ip>:<port>} from {@code Main}.
 * We tail the subprocess output, match that line, and use the URL
 * verbatim. Until the line appears the launcher shows a "Starting"
 * state with spinner; after a readiness TCP probe succeeds the state
 * flips to "Running".
 */
public class DesktopLauncher {

    // ---------- Lifecycle state ----------
    private enum State { STARTING, RUNNING, ERROR, STOPPED }

    private static final Pattern URL_LINE = Pattern.compile(
            "Service running on URL:\\s*(https?://[^\\s]+)");
    private static final int LOG_TAIL_MAX_LINES = 400;
    private static final int READINESS_TIMEOUT_MS = 45_000;

    // ---------- Swing surfaces ----------
    private JFrame frame;
    private JLabel statusDot;
    private JLabel statusText;
    private JLabel urlLabel;
    private JLabel dbPathLabel;
    private JLabel logPathLabel;
    private JButton openBtn;
    private JButton copyUrlBtn;
    private JButton exitBtn;
    private JButton toggleLogsBtn;
    private JButton themeBtn;
    private JPanel logsPanel;
    private JTextArea logArea;
    private JProgressBar progress;

    // ---------- Process + IO ----------
    private Process serverProcess;
    private Thread stdoutPump;
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private volatile boolean darkMode = false;

    // ---------- Paths ----------
    private File baseDir;
    private File appDir;
    private File dataDir;
    private File logsDir;
    private File bootJar;
    private File logFile;

    // ---------- Discovered at runtime ----------
    private volatile String serverUrl; // full http://host:port from server log

    // ---------- Rolling log buffer ----------
    private final Deque<String> logTail = new ArrayDeque<>();

    public static void main(String[] args) {
        try {
            FlatLightLaf.setup();
        } catch (Exception e) {
            System.err.println("Failed to initialize FlatLaf");
        }
        SwingUtilities.invokeLater(() -> {
            DesktopLauncher launcher = new DesktopLauncher();
            launcher.createAndShow();
            launcher.startServerAsync();
        });
    }

    // =====================================================================
    // UI construction
    // =====================================================================

    private void createAndShow() {
        resolveLayoutPaths();

        frame = new JFrame("Live Scoring System");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setSize(820, 620);
        frame.setMinimumSize(new Dimension(720, 520));
        frame.setLocationRelativeTo(null);
        frame.setIconImage(makeAppIcon(64));

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(18, 20, 18, 20));

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildCenter(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);

        frame.setContentPane(root);
        installKeyBindings(root);
        frame.setVisible(true);

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { onExit(); }
        });

        setState(State.STARTING, "Preparing to start server\u2026");
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBorder(new EmptyBorder(0, 0, 14, 0));

        JLabel icon = new JLabel(new ImageIcon(makeAppIcon(40)));
        header.add(icon, BorderLayout.WEST);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Live Scoring System");
        title.putClientProperty("FlatLaf.styleClass", "h1");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel subtitle = new JLabel("Local server control panel");
        subtitle.setForeground(new Color(120, 120, 130));
        titles.add(title);
        titles.add(subtitle);
        header.add(titles, BorderLayout.CENTER);

        themeBtn = new JButton("\u263D Dark");
        themeBtn.putClientProperty("JButton.buttonType", "borderless");
        themeBtn.setFocusPainted(false);
        themeBtn.addActionListener(e -> toggleTheme());
        header.add(themeBtn, BorderLayout.EAST);

        return header;
    }

    private JComponent buildCenter() {
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        // Status card
        JPanel statusCard = card();
        statusCard.setLayout(new BorderLayout(12, 0));

        statusDot = new JLabel("\u25CF");
        statusDot.setFont(statusDot.getFont().deriveFont(28f));
        statusDot.setForeground(new Color(200, 170, 50));

        JPanel statusText_ = new JPanel();
        statusText_.setOpaque(false);
        statusText_.setLayout(new BoxLayout(statusText_, BoxLayout.Y_AXIS));
        statusText = new JLabel("Starting\u2026");
        statusText.setFont(statusText.getFont().deriveFont(Font.BOLD, 16f));
        progress = new JProgressBar();
        progress.setIndeterminate(true);
        progress.setPreferredSize(new Dimension(240, 6));
        progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        statusText_.add(statusText);
        statusText_.add(Box.createVerticalStrut(6));
        statusText_.add(progress);

        statusCard.add(statusDot, BorderLayout.WEST);
        statusCard.add(statusText_, BorderLayout.CENTER);
        statusCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        center.add(statusCard);
        center.add(Box.createVerticalStrut(12));

        // Info card
        JPanel info = card();
        info.setLayout(new GridBagLayout());
        info.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 0;
        gc.gridy = 0;

        urlLabel = linkLabel("Waiting for server\u2026", () -> openInBrowser(serverUrl));
        dbPathLabel = linkLabel(pathString(dataDir), () -> openFile(dataDir));
        logPathLabel = linkLabel("Pending\u2026", () -> {
            if (logFile != null) openFile(logFile.getParentFile());
        });

        addInfoRow(info, gc, 0, "Server URL", urlLabel);
        addInfoRow(info, gc, 1, "Database", dbPathLabel);
        addInfoRow(info, gc, 2, "Logs", logPathLabel);

        JLabel ver = new JLabel("Version " + safeVersion());
        ver.setForeground(new Color(140, 140, 150));
        gc.gridx = 0; gc.gridy = 3; gc.gridwidth = 3;
        gc.insets = new Insets(10, 6, 0, 6);
        info.add(ver, gc);

        center.add(info);
        center.add(Box.createVerticalStrut(12));

        // Logs panel (collapsed by default)
        logsPanel = card();
        logsPanel.setLayout(new BorderLayout());
        logsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBackground(new Color(24, 26, 30));
        logArea.setForeground(new Color(220, 220, 220));
        JScrollPane sp = new JScrollPane(logArea);
        sp.setPreferredSize(new Dimension(1, 180));
        sp.setBorder(BorderFactory.createEmptyBorder());
        logsPanel.add(sp, BorderLayout.CENTER);
        logsPanel.setVisible(false);
        center.add(logsPanel);

        return center;
    }

    private JComponent buildFooter() {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttons.setBorder(new EmptyBorder(14, 0, 0, 0));

        openBtn = primaryButton("Open in Browser", e -> openInBrowser(serverUrl));
        openBtn.setEnabled(false);

        copyUrlBtn = new JButton("Copy URL");
        copyUrlBtn.setEnabled(false);
        copyUrlBtn.addActionListener(e -> copyUrl());

        JButton openLogsBtn = new JButton("Open Logs Folder");
        openLogsBtn.addActionListener(e -> openFile(logsDir));

        toggleLogsBtn = new JButton("Show Logs");
        toggleLogsBtn.addActionListener(e -> toggleLogsPanel());

        exitBtn = new JButton("Stop & Exit");
        exitBtn.addActionListener(e -> onExit());

        buttons.add(openBtn);
        buttons.add(copyUrlBtn);
        buttons.add(toggleLogsBtn);
        buttons.add(openLogsBtn);
        buttons.add(exitBtn);
        return buttons;
    }

    private JButton primaryButton(String text, java.awt.event.ActionListener l) {
        JButton b = new JButton(text);
        b.putClientProperty("JButton.buttonType", "roundRect");
        b.putClientProperty("JButton.backgroundHint", "default");
        b.addActionListener(l);
        return b;
    }

    private JPanel card() {
        JPanel p = new JPanel();
        p.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(210, 212, 218), 1, true),
                new EmptyBorder(14, 16, 14, 16)));
        p.setBackground(UIManager.getColor("Panel.background"));
        return p;
    }

    private void addInfoRow(JPanel parent, GridBagConstraints gc, int row, String label, JComponent value) {
        gc.gridy = row;
        gc.gridx = 0;
        gc.weightx = 0;
        JLabel lbl = new JLabel(label);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
        lbl.setPreferredSize(new Dimension(100, 22));
        parent.add(lbl, gc);
        gc.gridx = 1;
        gc.weightx = 1;
        parent.add(value, gc);
    }

    private JLabel linkLabel(String text, Runnable onClick) {
        JLabel l = new JLabel();
        setLinkText(l, text);
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (onClick != null) onClick.run(); }
            @Override public void mouseEntered(MouseEvent e) { l.setForeground(new Color(80, 140, 255)); }
            @Override public void mouseExited(MouseEvent e) { l.setForeground(null); }
        });
        return l;
    }

    private void setLinkText(JLabel l, String text) {
        l.setText("<html><a href='#'>" + escapeHtml(text) + "</a></html>");
    }

    private void installKeyBindings(JComponent root) {
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();
        int mod = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_O, mod), "open");
        am.put("open", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { openInBrowser(serverUrl); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, mod), "logs");
        am.put("logs", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { toggleLogsPanel(); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, mod | KeyEvent.SHIFT_DOWN_MASK), "copy");
        am.put("copy", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { copyUrl(); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Q, mod), "quit");
        am.put("quit", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { onExit(); }
        });
    }

    // =====================================================================
    // State machine
    // =====================================================================

    private void setState(State s, String message) {
        SwingUtilities.invokeLater(() -> {
            switch (s) {
                case STARTING:
                    statusDot.setForeground(new Color(230, 170, 40));
                    progress.setVisible(true);
                    break;
                case RUNNING:
                    statusDot.setForeground(new Color(50, 180, 90));
                    progress.setVisible(false);
                    openBtn.setEnabled(true);
                    copyUrlBtn.setEnabled(true);
                    break;
                case ERROR:
                    statusDot.setForeground(new Color(220, 70, 70));
                    progress.setVisible(false);
                    openBtn.setEnabled(false);
                    copyUrlBtn.setEnabled(false);
                    if (!logsPanel.isVisible()) toggleLogsPanel();
                    break;
                case STOPPED:
                    statusDot.setForeground(new Color(140, 140, 150));
                    progress.setVisible(false);
                    openBtn.setEnabled(false);
                    copyUrlBtn.setEnabled(false);
                    break;
            }
            statusText.setText(message);
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
            if (!bootJar.exists()) {
                setState(State.ERROR, "Boot jar not found: " + bootJar.getAbsolutePath());
                return;
            }
            if (!logsDir.exists()) Files.createDirectories(logsDir.toPath());
            String ts = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            logFile = logsDir.toPath().resolve("scoring-" + ts + ".log").toFile();
            SwingUtilities.invokeLater(() -> setLinkText(logPathLabel, logFile.getAbsolutePath()));

            String javaBin = Path.of(System.getProperty("java.home"), "bin",
                    isWindows() ? "java.exe" : "java").toString();

            ProcessBuilder pb = new ProcessBuilder(javaBin, "-jar", bootJar.getAbsolutePath());
            pb.directory(baseDir);
            pb.redirectErrorStream(true);
            // No redirectOutput() -- we pump stdout ourselves so we can both
            // mirror it to the log file AND scan for the URL/readiness line.

            serverProcess = pb.start();
            setState(State.STARTING, "Server starting\u2026");
            startStdoutPump();

            // Watch for early exit.
            serverProcess.onExit().thenAccept(p -> {
                if (stopping.get()) {
                    setState(State.STOPPED, "Server stopped.");
                    return;
                }
                int code = p.exitValue();
                setState(State.ERROR, "Server exited unexpectedly (code " + code + "). See logs.");
            });

            // Readiness probe runs in parallel; sets RUNNING when the socket accepts.
            new Thread(this::awaitReadiness, "readiness-probe").start();
        } catch (Exception ex) {
            setState(State.ERROR, "Failed to start server: " + ex.getMessage());
            appendLog("[launcher] startup threw: " + ex);
        }
    }

    private void startStdoutPump() {
        stdoutPump = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(serverProcess.getInputStream(), StandardCharsets.UTF_8));
                 PrintStream fileOut = new PrintStream(
                         new FileOutputStream(logFile, true), true, StandardCharsets.UTF_8)) {
                String line;
                while ((line = r.readLine()) != null) {
                    fileOut.println(line);
                    appendLog(line);
                    tryMatchServiceUrl(line);
                }
            } catch (IOException e) {
                if (!stopping.get()) appendLog("[launcher] stdout pump error: " + e.getMessage());
            }
        }, "stdout-pump");
        stdoutPump.setDaemon(true);
        stdoutPump.start();
    }

    private void tryMatchServiceUrl(String line) {
        if (serverUrl != null) return;
        Matcher m = URL_LINE.matcher(line);
        if (m.find()) {
            String u = m.group(1);
            // Normalize: server logs the physical IP, but for "open in browser"
            // on the operator's own machine we keep what the server printed
            // so it works both locally and for QR/URL sharing on the LAN.
            serverUrl = u;
            SwingUtilities.invokeLater(() -> setLinkText(urlLabel, u));
        }
    }

    private void awaitReadiness() {
        long deadline = System.currentTimeMillis() + READINESS_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (stopping.get()) return;
            if (serverProcess != null && !serverProcess.isAlive()) return; // onExit handles UI
            String url = serverUrl;
            if (url != null) {
                HostPort hp = parseHostPort(url);
                if (hp != null && canConnect(hp.host, hp.port, 400)) {
                    setState(State.RUNNING, "Server running at " + url);
                    return;
                }
            }
            sleep(400);
        }
        if (!stopping.get() && (serverUrl == null || !canConnect("127.0.0.1", 80, 200))) {
            setState(State.ERROR, "Server did not become ready within " +
                    (READINESS_TIMEOUT_MS / 1000) + "s. Check the logs.");
        }
    }

    private void stopServer() {
        stopping.set(true);
        try {
            if (serverProcess != null && serverProcess.isAlive()) {
                serverProcess.destroy();
                if (!serverProcess.waitFor(5, TimeUnit.SECONDS)) {
                    serverProcess.destroyForcibly();
                    serverProcess.waitFor(3, TimeUnit.SECONDS);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void onExit() {
        setState(State.STOPPED, "Stopping server\u2026");
        new Thread(() -> {
            stopServer();
            SwingUtilities.invokeLater(() -> {
                if (frame != null) frame.dispose();
                System.exit(0);
            });
        }, "shutdown").start();
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private void appendLog(String line) {
        synchronized (logTail) {
            logTail.addLast(line);
            while (logTail.size() > LOG_TAIL_MAX_LINES) logTail.removeFirst();
        }
        SwingUtilities.invokeLater(() -> {
            logArea.append(line + "\n");
            int over = logArea.getLineCount() - LOG_TAIL_MAX_LINES;
            if (over > 0) {
                try {
                    int end = logArea.getLineEndOffset(over - 1);
                    logArea.replaceRange("", 0, end);
                } catch (Exception ignored) {}
            }
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void toggleLogsPanel() {
        boolean show = !logsPanel.isVisible();
        logsPanel.setVisible(show);
        toggleLogsBtn.setText(show ? "Hide Logs" : "Show Logs");
        frame.revalidate();
    }

    private void toggleTheme() {
        try {
            darkMode = !darkMode;
            if (darkMode) FlatDarkLaf.setup(); else FlatLightLaf.setup();
            SwingUtilities.updateComponentTreeUI(frame);
            themeBtn.setText(darkMode ? "\u2600 Light" : "\u263D Dark");
        } catch (Exception e) {
            appendLog("[launcher] theme switch failed: " + e.getMessage());
        }
    }

    private void copyUrl() {
        if (serverUrl == null) return;
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(serverUrl), null);
        flashStatus("Copied " + serverUrl + " to clipboard");
    }

    private void flashStatus(String msg) {
        String prev = statusText.getText();
        statusText.setText(msg);
        Timer t = new Timer(1800, e -> statusText.setText(prev));
        t.setRepeats(false);
        t.start();
    }

    private void resolveLayoutPaths() {
        try {
            File where = new File(DesktopLauncher.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            File jarDir = where.getParentFile();
            if (jarDir != null && jarDir.getName().equalsIgnoreCase("app")) {
                baseDir = jarDir.getParentFile();
            } else {
                baseDir = new File(System.getProperty("user.dir"));
            }
        } catch (Exception e) {
            baseDir = new File(System.getProperty("user.dir"));
        }
        appDir = new File(baseDir, "app");
        dataDir = new File(baseDir, "data");
        logsDir = new File(baseDir, "logs");
        bootJar = new File(appDir, "scoring-system.jar");
    }

    private void openInBrowser(String url) {
        if (url == null) return;
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ex) {
            flashStatus("Open browser failed: " + ex.getMessage());
        }
    }

    private void openFile(File f) {
        try {
            if (f != null && f.exists()) Desktop.getDesktop().open(f);
        } catch (Exception ex) {
            flashStatus("Open failed: " + ex.getMessage());
        }
    }

    private static HostPort parseHostPort(String url) {
        try {
            URI u = URI.create(url);
            int port = u.getPort();
            if (port < 0) port = "https".equalsIgnoreCase(u.getScheme()) ? 443 : 80;
            String host = u.getHost();
            if (host == null) return null;
            return new HostPort(host, port);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean canConnect(String host, int port, int timeoutMs) {
        try (Socket s = new Socket()) {
            s.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private static String pathString(File f) {
        return f == null ? "-" : f.getAbsolutePath();
    }

    private static String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String safeVersion() {
        try {
            String v = DesktopLauncher.class.getPackage().getImplementationVersion();
            return v != null ? v : "dev";
        } catch (Exception e) {
            return "dev";
        }
    }

    private BufferedImage makeAppIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        // Gradient background circle
        GradientPaint gp = new GradientPaint(0, 0, new Color(55, 120, 220),
                size, size, new Color(90, 60, 210));
        g.setPaint(gp);
        g.fillRoundRect(0, 0, size, size, size / 4, size / 4);
        // "LS" monogram
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (size * 0.5)));
        FontMetrics fm = g.getFontMetrics();
        String s = "LS";
        int w = fm.stringWidth(s);
        g.drawString(s, (size - w) / 2, (size + fm.getAscent() - fm.getDescent()) / 2);
        g.dispose();
        return img;
    }

    private static final class HostPort {
        final String host; final int port;
        HostPort(String h, int p) { this.host = h; this.port = p; }
    }

    // Kept for backwards-compat callers that expect this static (e.g. tests).
    @SuppressWarnings("unused")
    private String resolveLocalIPv4() {
        try { return InetAddress.getLocalHost().getHostAddress(); }
        catch (Exception e) { return "127.0.0.1"; }
    }

    @SuppressWarnings("unused")
    private static OutputStreamWriter utf8(FileOutputStream fos) {
        return new OutputStreamWriter(fos, StandardCharsets.UTF_8);
    }
}
