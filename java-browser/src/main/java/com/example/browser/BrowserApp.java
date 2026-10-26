package com.example.browser;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;
import java.util.regex.Pattern;

public class BrowserApp extends Application {
    private static boolean smoke;

    static void begin(String[] args) {
        smoke = Arrays.asList(args).contains("--smoke");
        Application.launch(BrowserApp.class, args);
    }

    /** Persistent settings in ~/.freebrowser */
    static class Settings {
        final Path dir = Paths.get(System.getProperty("user.home"), ".freebrowser");
        final Path file = dir.resolve("settings.properties");
        final Path scripts = dir.resolve("scripts");
        String home = "https://duckduckgo.com";
        String search = "https://duckduckgo.com/?q=%s";
        String ua = "";

        void load() {
            try {
                Files.createDirectories(scripts);
                if (Files.exists(file)) {
                    Properties p = new Properties();
                    try (InputStream in = Files.newInputStream(file)) { p.load(in); }
                    home = p.getProperty("home", home);
                    search = p.getProperty("search", search);
                    ua = p.getProperty("ua", ua);
                }
            } catch (IOException ignored) { }
        }

        void save() {
            try {
                Files.createDirectories(dir);
                Properties p = new Properties();
                p.setProperty("home", home);
                p.setProperty("search", search);
                p.setProperty("ua", ua);
                try (OutputStream out = Files.newOutputStream(file)) { p.store(out, "FreeBrowser"); }
            } catch (IOException ignored) { }
        }
    }

    private final Settings st = new Settings();
    private TabPane tabs;
    private TextField addr;
    private ProgressBar bar;
    private Stage stage;

    private WebEngine engine(Tab t) { return ((WebView) t.getContent()).getEngine(); }

    private WebEngine current() {
        Tab t = tabs.getSelectionModel().getSelectedItem();
        return t == null ? null : engine(t);
    }

    String resolve(String in) {
        String t = in == null ? "" : in.trim();
        if (t.isEmpty()) return st.home;
        if (t.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*") || t.startsWith("about:")) return t;
        if (!t.contains(" ") && (t.contains(".") || t.startsWith("localhost"))) return "https://" + t;
        return st.search.replace("%s", URLEncoder.encode(t, StandardCharsets.UTF_8));
    }

    private Tab addTab(String input) {
        WebView wv = new WebView();
        WebEngine eng = wv.getEngine();
        if (!st.ua.isBlank()) eng.setUserAgent(st.ua);
        Tab tab = new Tab("New tab", wv);
        eng.titleProperty().addListener((o, a, b) -> {
            String s = (b == null || b.isBlank()) ? "New tab" : b;
            tab.setText(s.length() > 22 ? s.substring(0, 22) + "…" : s);
        });
        eng.locationProperty().addListener((o, a, b) -> {
            if (tab == tabs.getSelectionModel().getSelectedItem() && b != null && !addr.isFocused()) addr.setText(b);
        });
        eng.getLoadWorker().stateProperty().addListener((o, a, s) -> {
            if (s == Worker.State.SUCCEEDED) injectScripts(eng);
        });
        // target=_blank / window.open -> new tab
        eng.setCreatePopupHandler(cfg -> engine(addTab(null)));
        tab.setOnClosed(e -> {
            eng.load(null);
            if (tabs.getTabs().isEmpty()) addTab(null);
        });
        tabs.getTabs().add(tab);
        tabs.getSelectionModel().select(tab);
        if (input != null) eng.load(resolve(input));
        return tab;
    }

    // ---- user scripts: ~/.freebrowser/scripts/*.js, first "// @match <glob>" line (default <all_urls>) ----
    static Pattern glob(String g) {
        if (g.isEmpty() || g.equals("<all_urls>")) return Pattern.compile("^https?://.*");
        String[] parts = g.split("\\*", -1);
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(".*");
            sb.append(Pattern.quote(parts[i]));
        }
        return Pattern.compile(sb.append("$").toString());
    }

    private void injectScripts(WebEngine eng) {
        String loc = eng.getLocation();
        if (loc == null || !loc.startsWith("http")) return;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(st.scripts, "*.js")) {
            for (Path p : ds) {
                String code = Files.readString(p);
                String match = "<all_urls>";
                for (String line : code.split("\n", 20)) {
                    line = line.trim();
                    if (line.startsWith("// @match")) { match = line.substring(9).trim(); break; }
                }
                if (!glob(match).matcher(loc).matches()) continue;
                try {
                    eng.executeScript("(function(){try{\n" + code + "\n}catch(e){console.error(e)}})();");
                } catch (Exception ex) {
                    System.err.println("Script " + p.getFileName() + " failed: " + ex);
                }
            }
        } catch (IOException ignored) { }
    }

    private void showSettings() {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Settings");
        d.initOwner(stage);
        TextField home = new TextField(st.home);
        TextField search = new TextField(st.search);
        TextField ua = new TextField(st.ua);
        ua.setPromptText("blank = default");
        home.setPrefColumnCount(30);
        Button open = new Button("Open scripts folder");
        open.setOnAction(e -> getHostServices().showDocument(st.scripts.toUri().toString()));
        Label hint = new Label("Scripts (extensions): put .js files in the scripts folder. Optional first line: "
            + "// @match https://*.example.com/*  (default: all sites). Reload a page to apply.");
        hint.setWrapText(true);
        hint.setMaxWidth(440);
        GridPane g = new GridPane();
        g.setHgap(8); g.setVgap(8); g.setPadding(new Insets(12));
        g.addRow(0, new Label("Home page"), home);
        g.addRow(1, new Label("Search URL (%s = query)"), search);
        g.addRow(2, new Label("User agent"), ua);
        g.add(open, 1, 3);
        g.add(hint, 0, 4, 2, 1);
        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                st.home = home.getText().trim();
                st.search = search.getText().trim();
                st.ua = ua.getText().trim();
                st.save();
                for (Tab t : tabs.getTabs()) engine(t).setUserAgent(st.ua.isBlank() ? null : st.ua);
            }
        });
    }

    private void go() {
        WebEngine e = current();
        if (e != null) e.load(resolve(addr.getText()));
    }

    @Override
    public void start(Stage primary) {
        stage = primary;
        st.load();
        tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        Button back = new Button("<"), fwd = new Button(">"), reload = new Button("Reload"),
               home = new Button("Home"), plus = new Button("+"), gear = new Button("Settings");
        addr = new TextField();
        addr.setPromptText("Search or enter address");
        HBox.setHgrow(addr, Priority.ALWAYS);
        addr.setOnAction(e -> go());

        back.setOnAction(e -> {
            WebEngine en = current();
            if (en == null) return;
            WebHistory h = en.getHistory();
            if (h.getCurrentIndex() > 0) h.go(-1);
        });
        fwd.setOnAction(e -> {
            WebEngine en = current();
            if (en == null) return;
            WebHistory h = en.getHistory();
            if (h.getCurrentIndex() < h.getEntries().size() - 1) h.go(1);
        });
        reload.setOnAction(e -> { WebEngine en = current(); if (en != null) en.reload(); });
        home.setOnAction(e -> { WebEngine en = current(); if (en != null) en.load(resolve(st.home)); });
        plus.setOnAction(e -> addTab(st.home));
        gear.setOnAction(e -> showSettings());

        bar = new ProgressBar(0);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setPrefHeight(4);

        tabs.getSelectionModel().selectedItemProperty().addListener((o, a, t) -> {
            bar.progressProperty().unbind();
            bar.visibleProperty().unbind();
            if (t == null) return;
            WebEngine en = engine(t);
            addr.setText(en.getLocation() == null ? "" : en.getLocation());
            bar.progressProperty().bind(en.getLoadWorker().progressProperty());
            bar.visibleProperty().bind(en.getLoadWorker().runningProperty());
        });

        HBox toolbar = new HBox(6, back, fwd, reload, home, addr, plus, gear);
        toolbar.setPadding(new Insets(8));
        BorderPane root = new BorderPane();
        root.setTop(new VBox(toolbar, bar));
        root.setCenter(tabs);

        Scene scene = new Scene(root, 1100, 750);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.T, KeyCombination.SHORTCUT_DOWN), () -> addTab(st.home));
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN), () -> {
            Tab t = tabs.getSelectionModel().getSelectedItem();
            if (t != null) { tabs.getTabs().remove(t); if (tabs.getTabs().isEmpty()) addTab(st.home); }
        });
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN), () -> {
            addr.requestFocus(); addr.selectAll();
        });
        primary.setTitle("FreeBrowser");
        primary.setScene(scene);
        primary.show();

        addTab(smoke ? null : st.home);

        if (smoke) {
            WebEngine en = current();
            en.getLoadWorker().stateProperty().addListener((o, a, s) -> {
                if (s == Worker.State.SUCCEEDED) {
                    System.out.println("SMOKE_OK");
                    Platform.exit();
                    System.exit(0);
                }
            });
            en.loadContent("<html><body>smoke</body></html>");
            Thread t = new Thread(() -> {
                try { Thread.sleep(45000); } catch (InterruptedException ignored) { }
                System.out.println("SMOKE_TIMEOUT");
                Runtime.getRuntime().halt(3);
            });
            t.setDaemon(true);
            t.start();
        }
    }
}
