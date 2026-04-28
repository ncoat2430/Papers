import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * PAPERS, PLEASE — Java Pixel Art Edition
 * Compile: javac PapersPlease.java
 * Run:     java PapersPlease
 */
public class PapersPlease extends JFrame {

    // ── Pixel-art colour palette ─────────────────────────────────────────────
    static final Color C_BG        = new Color(0x0D0B09);
    static final Color C_BG2       = new Color(0x151210);
    static final Color C_PANEL     = new Color(0x1C1810);
    static final Color C_BORDER    = new Color(0x3A2E1A);
    static final Color C_ACCENT    = new Color(0x8B6914);
    static final Color C_PAPER     = new Color(0xBAAB85);
    static final Color C_PAPER_DK  = new Color(0x9A8B6A);
    static final Color C_INK       = new Color(0x1A1208);
    static final Color C_RED       = new Color(0x6B1010);
    static final Color C_RED_STAMP = new Color(0x9B1A00);
    static final Color C_GREEN_ST  = new Color(0x0A4A14);
    static final Color C_TEXT      = new Color(0x9A8A6A);
    static final Color C_TEXT_DIM  = new Color(0x4A3C28);
    static final Color C_FLOOR     = new Color(0x4A3C2C);
    static final Color C_WALL      = new Color(0x2A2218);
    static final Color C_DESK      = new Color(0x3A2C18);

    // ── Pixel font (monospaced) ───────────────────────────────────────────────
    static Font FONT_PIXEL;
    static Font FONT_PIXEL_SM;
    static Font FONT_PIXEL_LG;
    static Font FONT_PIXEL_XL;

    static {
        // Fallback to Courier Bold if no pixel font available
        FONT_PIXEL    = new Font("Courier New", Font.BOLD, 13);
        FONT_PIXEL_SM = new Font("Courier New", Font.BOLD, 10);
        FONT_PIXEL_LG = new Font("Courier New", Font.BOLD, 18);
        FONT_PIXEL_XL = new Font("Courier New", Font.BOLD, 32);
    }

    // ── Cards ─────────────────────────────────────────────────────────────────
    static final String CARD_INTRO  = "INTRO";
    static final String CARD_DIFF   = "DIFFICULTY";
    static final String CARD_GAME   = "GAME";

    // ── Game data ─────────────────────────────────────────────────────────────
    static final String[] COUNTRIES = {"Arstotzka","Kolechian","Impor","Antegria","Republia","Kolechia","Obristan"};
    static final String[] OCCUPATIONS = {"Trabajador","Diplomático","Turista","Residente","Comerciante","Periodista","Médico"};
    static final String[] FIRSTNAMES = {"Canserbero","Yuritza","Carlos","Dima","Nadia","Boris","Lyudmila","Andrei","Tanya","Oleg","Elena","Pavel","Vera","Gregor","Sonya"};
    static final String[] LASTNAMES = {"Volkov","Koval","Stasik","Bruzek","Mordin","Omin","Lavik","Cherny","Drakon","Vasil","Novak","Petrov","Zoltan","Krupov"};

    // ── Difficulty config ─────────────────────────────────────────────────────
    record DiffConfig(int lives, double fakeChance, int npcsPerDay, int allowedCountries) {}
    static final Map<String, DiffConfig> DIFF_MAP = Map.of(
        "FÁCIL",   new DiffConfig(4, 0.30, 5, 4),
        "NORMAL",  new DiffConfig(3, 0.45, 7, 3),
        "DIFÍCIL", new DiffConfig(2, 0.60, 8, 2)
    );

    // ── State ─────────────────────────────────────────────────────────────────
    record NpcDoc(String country, String name, String occupation, int birthYear, int expYear) {}

    static class Npc {
        int id; String name, fname, lname;
        String gender, occupation, realCountry;
        int birthYear, expYear;
        NpcDoc doc;
        boolean isFake; String fakeReason;
        Color torso, leg, skin;
        List<String> greetings;
        // animation
        double x; boolean walking, arrived;
    }

    // game state
    int lives, approved, denied, errors, day;
    String difficulty = "NORMAL";
    Queue<Npc> npcQueue = new LinkedList<>();
    Npc currentNpc;
    boolean npcArrived, processing;
    int processedNpcs, totalNpcs;
    List<String> allowedCountries = new ArrayList<>();
    int npcIdCounter = 0;

    // stamp flash
    String stampFlash = null; // "APROBADO" or "DENEGADO"
    long flashStart = 0;
    Color flashColor = C_GREEN_ST;

    // speech bubble
    String speechText = null;
    long speechStart = 0;

    // npc walk
    javax.swing.Timer walkTimer;
    double npcTargetX;

    // panels
    CardLayout cards;
    JPanel cardRoot;
    GamePanel gamePanel;

    // doc modal state
    boolean docModalOpen = false;
    boolean agendaOpen = false;
    boolean resultOpen = false;
    boolean toolsActive = false;

    String resultIcon, resultTitle, resultMsg, resultScore;
    boolean isGameOver = false;

    // doc questions answers
    String docAnswer = "";
    List<String[]> docQuestions = new ArrayList<>(); // [question, answer]

    // ─────────────────────────────────────────────────────────────────────────
    public PapersPlease() {
        super("PAPERS, PLEASE");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(960, 640);
        setMinimumSize(new Dimension(800, 560));
        setLocationRelativeTo(null);
        setBackground(C_BG);

        cards = new CardLayout();
        cardRoot = new JPanel(cards);
        cardRoot.setBackground(C_BG);

        cardRoot.add(buildIntroPanel(),  CARD_INTRO);
        cardRoot.add(buildDiffPanel(),   CARD_DIFF);
        gamePanel = new GamePanel();
        cardRoot.add(gamePanel,          CARD_GAME);

        add(cardRoot);
        setVisible(true);
    }

    void showCard(String name) {
        cards.show(cardRoot, name);
        if (name.equals(CARD_GAME)) gamePanel.requestFocusInWindow();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  INTRO PANEL
    // ═══════════════════════════════════════════════════════════════════════
    JPanel buildIntroPanel() {
        JPanel p = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                // dark radial bg
                int w = getWidth(), h = getHeight();
                g2.setColor(C_BG);
                g2.fillRect(0,0,w,h);
                // noise scanlines
                g2.setColor(new Color(255,255,255,6));
                for (int y = 0; y < h; y += 4) g2.drawLine(0, y, w, y);
                // vignette
                RadialGradientPaint vg = new RadialGradientPaint(
                    new Point(w/2, h/2), Math.max(w,h)*0.7f,
                    new float[]{0f, 1f},
                    new Color[]{new Color(0,0,0,0), new Color(0,0,0,180)}
                );
                g2.setPaint(vg);
                g2.fillRect(0,0,w,h);
            }
        };
        p.setBackground(C_BG);
        // Build components using absolute layout with a resize listener
        JLabel titleLbl = new JLabel("PAPERS, PLEASE", SwingConstants.CENTER) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // pixel shadow
                g2.setFont(new Font("Courier New", Font.BOLD, 52));
                g2.setColor(Color.BLACK);
                g2.drawString(getText(), 4, getHeight()-4+2);
                g2.setColor(C_ACCENT);
                g2.drawString(getText(), 2, getHeight()-4);
            }
        };
        titleLbl.setFont(new Font("Courier New", Font.BOLD, 52));
        titleLbl.setForeground(C_ACCENT);

        JLabel subtitleLbl = new JLabel("PUESTO FRONTERIZO — ARSTOTZKA", SwingConstants.CENTER);
        subtitleLbl.setFont(FONT_PIXEL_SM);
        subtitleLbl.setForeground(C_TEXT_DIM);

        // Flag pixel-art (drawn in a custom component)
        FlagPixelPanel flag = new FlagPixelPanel();

        PixelButton btnStart = new PixelButton("INICIAR", C_RED, C_PAPER);
        btnStart.addActionListener(e -> showCard(CARD_DIFF));

        // Layout
        p.add(flag);
        p.add(titleLbl);
        p.add(subtitleLbl);
        p.add(btnStart);

        p.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                int w = p.getWidth(), h = p.getHeight();
                int cx = w / 2;
                flag.setBounds(cx - 80, h/2 - 230, 160, 100);
                titleLbl.setBounds(cx - 310, h/2 - 110, 620, 70);
                subtitleLbl.setBounds(cx - 220, h/2 - 30, 440, 24);
                btnStart.setBounds(cx - 120, h/2 + 40, 240, 52);
            }
        });
        return p;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  DIFFICULTY PANEL
    // ═══════════════════════════════════════════════════════════════════════
    JPanel buildDiffPanel() {
        JPanel p = new JPanel(null);
        p.setBackground(C_BG);

        JLabel title = new JLabel("SELECCIONAR DIFICULTAD", SwingConstants.CENTER);
        title.setFont(FONT_PIXEL_LG);
        title.setForeground(C_ACCENT);

        JLabel sub = new JLabel("Elige tu nivel de alerta, ciudadano", SwingConstants.CENTER);
        sub.setFont(FONT_PIXEL_SM);
        sub.setForeground(C_TEXT_DIM);

        String[][] diffs = {
            {"FÁCIL",   "4 VIDAS\nDiferencias obvias\nTiempo generoso",   "#1A5C1A"},
            {"NORMAL",  "3 VIDAS\nAlgunas trampas\nTiempo estándar",      "#8B6914"},
            {"DIFÍCIL", "2 VIDAS\nMuy engañoso\nSin margen",              "#6B1010"}
        };
        Color[] topColors = {C_GREEN_ST, C_ACCENT, C_RED};
        DiffCard[] cards_arr = new DiffCard[3];

        for (int i = 0; i < 3; i++) {
            final String diffName = diffs[i][0];
            DiffCard card = new DiffCard(diffs[i][0], diffs[i][1], topColors[i]);
            card.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { startGame(diffName); }
            });
            cards_arr[i] = card;
            p.add(card);
        }

        PixelButton backBtn = new PixelButton("← VOLVER", C_PANEL, C_TEXT);
        backBtn.addActionListener(e -> showCard(CARD_INTRO));

        p.add(title);
        p.add(sub);
        p.add(backBtn);

        p.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                int w = p.getWidth(), h = p.getHeight();
                int cx = w / 2;
                title.setBounds(cx - 250, h/2 - 160, 500, 40);
                sub.setBounds(cx - 200, h/2 - 110, 400, 24);
                int cardW = 160, cardH = 180, gap = 20;
                int totalW = cardW * 3 + gap * 2;
                int startX = cx - totalW / 2;
                for (int i = 0; i < 3; i++) {
                    cards_arr[i].setBounds(startX + i * (cardW + gap), h/2 - 80, cardW, cardH);
                }
                backBtn.setBounds(cx - 90, h/2 + 130, 180, 40);
            }
        });
        return p;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  START GAME
    // ═══════════════════════════════════════════════════════════════════════
    void startGame(String diff) {
        difficulty = diff;
        DiffConfig cfg = DIFF_MAP.get(diff);
        lives = cfg.lives(); approved = denied = errors = 0; day = 1;
        processedNpcs = 0; processing = false; npcArrived = false;
        currentNpc = null; docModalOpen = false; agendaOpen = false;
        resultOpen = false; toolsActive = false; docAnswer = "";
        npcQueue.clear();

        // Pick allowed countries
        List<String> shuffled = new ArrayList<>(Arrays.asList(COUNTRIES));
        Collections.shuffle(shuffled);
        allowedCountries = shuffled.subList(0, cfg.allowedCountries());

        generateQueue();
        showCard(CARD_GAME);
        gamePanel.repaint();
        javax.swing.Timer t = new javax.swing.Timer(600, e2 -> spawnNext());
        t.setRepeats(false); t.start();
    }

    void generateQueue() {
        DiffConfig cfg = DIFF_MAP.get(difficulty);
        npcQueue.clear();
        for (int i = 0; i < cfg.npcsPerDay(); i++) {
            npcQueue.add(generateNpc(Math.random() < cfg.fakeChance()));
        }
        totalNpcs = npcQueue.size();
    }

    Npc generateNpc(boolean isFake) {
        Npc n = new Npc();
        n.id = ++npcIdCounter;
        n.gender = Math.random() > 0.5 ? "M" : "F";
        n.fname = FIRSTNAMES[(int)(Math.random() * FIRSTNAMES.length)];
        n.lname = LASTNAMES[(int)(Math.random() * LASTNAMES.length)];
        n.name = n.fname + " " + n.lname;
        n.occupation = OCCUPATIONS[(int)(Math.random() * OCCUPATIONS.length)];
        n.realCountry = allowedCountries.get((int)(Math.random() * allowedCountries.size()));
        n.birthYear = 1940 + (int)(Math.random() * 60);
        n.expYear = 2026 + (int)(Math.random() * 4) + 1;

        List<String> forbidden = new ArrayList<>(Arrays.asList(COUNTRIES));
        forbidden.removeAll(allowedCountries);
        String forbiddenCountry = forbidden.isEmpty() ? COUNTRIES[COUNTRIES.length-1]
            : forbidden.get((int)(Math.random() * forbidden.size()));

        String docCountry = n.realCountry, docName = n.name;
        String docOccupation = n.occupation;
        int docExpYear = n.expYear; n.isFake = isFake;

        if (isFake) {
            int t = (int)(Math.random() * 4);
            if (t == 0) { docCountry = forbiddenCountry; n.fakeReason = "País no permitido: " + forbiddenCountry; }
            else if (t == 1) { docExpYear = 2023 + (int)(Math.random() * 2); n.fakeReason = "Documento vencido (exp: " + docExpYear + ")"; }
            else if (t == 2) {
                docName = FIRSTNAMES[(int)(Math.random()*FIRSTNAMES.length)] + " " + LASTNAMES[(int)(Math.random()*LASTNAMES.length)];
                n.fakeReason = "Nombre no coincide";
            } else {
                String[] others = Arrays.stream(OCCUPATIONS).filter(o -> !o.equals(n.occupation)).toArray(String[]::new);
                docOccupation = others[(int)(Math.random()*others.length)];
                n.fakeReason = "Ocupación inconsistente";
            }
        }

        n.doc = new NpcDoc(docCountry, docName, docOccupation, n.birthYear, docExpYear);

        // pixel-art body colors
        Color[][] palettes = {
            {new Color(0x2A4A6A), new Color(0x1A2A4A)},
            {new Color(0x4A2A1A), new Color(0x2A1A0A)},
            {new Color(0x2A4A2A), new Color(0x1A2A1A)},
            {new Color(0x4A3A1A), new Color(0x2A1A0A)},
            {new Color(0x3A2A4A), new Color(0x1A0A2A)}
        };
        Color[] pal = palettes[(int)(Math.random() * palettes.length)];
        n.torso = pal[0]; n.leg = pal[1];
        int[] skins = {0xC49A6A, 0xB88558, 0xD8B488, 0x7A5030, 0xB06838};
        n.skin = new Color(skins[(int)(Math.random() * skins.length)]);

        n.greetings = List.of(
            "Buenos días...", "Aquí mis documentos.", "Espero que todo esté en orden.",
            "He viajado muy lejos.", "¿Qué necesita?"
        );
        n.walking = false; n.arrived = false;
        return n;
    }

    // ── NPC movement ──────────────────────────────────────────────────────────
    void spawnNext() {
        if (npcQueue.isEmpty()) { endDay(); return; }
        if (processing) return;

        currentNpc = npcQueue.poll();
        npcArrived = false; processing = true;
        currentNpc.x = -120; currentNpc.walking = true; currentNpc.arrived = false;

        int sceneW = gamePanel.getWidth();
        npcTargetX = sceneW / 2.0 - 60;

        walkTimer = new javax.swing.Timer(16, null);
        walkTimer.addActionListener(e -> {
            currentNpc.x += 3;
            gamePanel.repaint();
            if (currentNpc.x >= npcTargetX) {
                walkTimer.stop();
                currentNpc.walking = false; currentNpc.arrived = true;
                npcArrived();
            }
        });
        walkTimer.start();
    }

    void npcArrived() {
        npcArrived = true;
        speechText = currentNpc.greetings.get((int)(Math.random() * currentNpc.greetings.size()));
        speechStart = System.currentTimeMillis();
        javax.swing.Timer t = new javax.swing.Timer(800, e -> {
            toolsActive = true; gamePanel.repaint();
        });
        t.setRepeats(false); t.start();
        gamePanel.repaint();
    }

    // ── Stamp ─────────────────────────────────────────────────────────────────
    void stamp(boolean approve) {
        if (!npcArrived || currentNpc == null) return;
        Npc npc = currentNpc;
        boolean correct = (approve && !npc.isFake) || (!approve && npc.isFake);

        stampFlash = approve ? "APROBADO" : "DENEGADO";
        flashColor = approve ? C_GREEN_ST : C_RED_STAMP;
        flashStart = System.currentTimeMillis();

        if (correct) {
            if (approve) this.approved++; else denied++;
        } else {
            errors++; lives--;
            if (approve) this.approved++; else denied++;
            // show wrong speech
            speechText = approve ? "¡Ja! ¡Los engañé a todos!" : "¡Mis documentos son válidos!";
            speechStart = System.currentTimeMillis();
        }

        toolsActive = false; docModalOpen = false; npcArrived = false;
        boolean wasAlive = lives > 0;
        currentNpc = null; processedNpcs++;

        // animate exit
        Npc exitNpc = npc;
        double dir = approve ? 1 : -1;
        javax.swing.Timer exitTimer = new javax.swing.Timer(16, null);
        exitTimer.addActionListener(e -> {
            exitNpc.x += 5 * dir;
            gamePanel.repaint();
            if (exitNpc.x > gamePanel.getWidth() + 150 || exitNpc.x < -150) {
                exitTimer.stop();
            }
        });
        exitTimer.start();

        gamePanel.repaint();

        if (!wasAlive) {
            javax.swing.Timer t = new javax.swing.Timer(800, e -> gameOver());
            t.setRepeats(false); t.start();
            return;
        }

        processing = false;
        if (npcQueue.isEmpty()) {
            javax.swing.Timer t = new javax.swing.Timer(1200, e -> endDay());
            t.setRepeats(false); t.start();
        } else {
            javax.swing.Timer t = new javax.swing.Timer(1400, e -> spawnNext());
            t.setRepeats(false); t.start();
        }
    }

    // ── Day end / game over ───────────────────────────────────────────────────
    void endDay() {
        int accuracy = processedNpcs > 0 ? (int)((double)(processedNpcs - errors) / processedNpcs * 100) : 100;
        resultIcon = errors == 0 ? "★" : errors < 3 ? "!" : "✗";
        resultTitle = "FIN DEL DÍA " + day;
        resultMsg = "Personas: " + processedNpcs + "   Errores: " + errors;
        resultScore = "Precisión: " + accuracy + "%   Aprobados: " + approved + "   Rechazados: " + denied;
        isGameOver = false;
        resultOpen = true;
        gamePanel.repaint();
    }

    void gameOver() {
        resultIcon = "✗";
        resultTitle = "PUESTO CERRADO";
        resultMsg = "Demasiados errores. El puesto ha sido sancionado.";
        resultScore = "Aprobados: " + approved + "   Rechazados: " + denied + "   Errores: " + errors;
        isGameOver = true; toolsActive = false;
        resultOpen = true;
        gamePanel.repaint();
    }

    void continueGame() {
        if (isGameOver) { resultOpen = false; showCard(CARD_INTRO); return; }
        resultOpen = false; day++;
        processedNpcs = 0; npcQueue.clear();
        currentNpc = null; npcArrived = false; toolsActive = false; docModalOpen = false;

        List<String> sh = new ArrayList<>(Arrays.asList(COUNTRIES));
        Collections.shuffle(sh);
        int allowed = DIFF_MAP.get(difficulty).allowedCountries();
        allowedCountries = sh.subList(0, allowed);

        generateQueue(); processing = false;
        gamePanel.repaint();
        javax.swing.Timer t = new javax.swing.Timer(600, e -> spawnNext());
        t.setRepeats(false); t.start();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  GAME PANEL — custom pixel-art renderer
    // ═══════════════════════════════════════════════════════════════════════
    class GamePanel extends JPanel implements MouseListener {
        static final int HUD_H = 38;
        static final int BAR_H = 72;

        // Hover states
        boolean hoverApprove, hoverDeny, hoverDoc, hoverAgenda;
        int hoverDayX;

        GamePanel() {
            setBackground(C_BG);
            addMouseListener(this);
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    if (docModalOpen || agendaOpen || resultOpen) return;
                    int w = getWidth(), h = getHeight();
                    Rectangle[] areas = getToolAreas(w, h);
                    hoverApprove = toolsActive && areas[0].contains(e.getPoint());
                    hoverDeny    = toolsActive && areas[1].contains(e.getPoint());
                    hoverDoc     = toolsActive && areas[2].contains(e.getPoint());
                    hoverAgenda  = areas[3].contains(e.getPoint());
                    repaint();
                }
            });
        }

        // Returns [approveBtn, denyBtn, docBtn, agendaBtn]
        Rectangle[] getToolAreas(int w, int h) {
            int by = h - BAR_H + (BAR_H - 54) / 2;
            int cx = w / 2;
            return new Rectangle[]{
                new Rectangle(cx - 120, by, 54, 54),  // approve
                new Rectangle(cx - 60,  by, 54, 54),  // deny
                new Rectangle(cx + 10,  by, 54, 54),  // doc
                new Rectangle(cx + 80,  by, 54, 54),  // agenda
            };
        }

        @Override
        protected void paintComponent(Graphics gx) {
            super.paintComponent(gx);
            Graphics2D g = (Graphics2D) gx;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            int w = getWidth(), h = getHeight();

            drawHUD(g, w);
            drawScene(g, w, h);
            drawToolbar(g, w, h);

            // stamp flash
            if (stampFlash != null) {
                long elapsed = System.currentTimeMillis() - flashStart;
                if (elapsed < 900) {
                    float t2 = elapsed / 900f;
                    float alpha = t2 < 0.3f ? t2/0.3f : t2 > 0.7f ? (1 - (t2-0.7f)/0.3f) : 1f;
                    drawStampFlash(g, w, h, alpha);
                    repaint();
                } else { stampFlash = null; }
            }

            // Doc modal
            if (docModalOpen) drawDocModal(g, w, h);

            // Agenda
            if (agendaOpen) drawAgenda(g, w, h);

            // Result overlay
            if (resultOpen) drawResult(g, w, h);

            // Scanlines
            g.setColor(new Color(0, 0, 0, 20));
            for (int y = 0; y < h; y += 3) g.drawLine(0, y, w, y);
        }

        // ── HUD ───────────────────────────────────────────────────────────────
        void drawHUD(Graphics2D g, int w) {
            g.setColor(C_BG);
            g.fillRect(0, 0, w, HUD_H);
            g.setColor(C_BORDER);
            g.drawLine(0, HUD_H - 1, w, HUD_H - 1);

            // Progress bar
            if (totalNpcs > 0) {
                int barW = (int)((double) processedNpcs / totalNpcs * w);
                g.setColor(C_ACCENT);
                g.fillRect(0, HUD_H - 3, barW, 3);
            }

            g.setFont(FONT_PIXEL_SM);
            int y = HUD_H / 2 + 4;

            hudItem(g, 14, y, "DÍA", String.valueOf(day));
            hudItem(g, 100, y, "APROBADOS", String.valueOf(approved));
            hudItem(g, 230, y, "RECHAZADOS", String.valueOf(denied));
            hudItem(g, 370, y, "ERRORES", String.valueOf(errors));

            // Lives
            int lx = w - 20;
            g.setColor(C_TEXT_DIM); g.setFont(FONT_PIXEL_SM);
            g.drawString("VIDAS", lx - 95, y);
            int maxLives = DIFF_MAP.get(difficulty).lives();
            for (int i = 0; i < maxLives; i++) {
                g.setColor(i < lives ? C_RED : new Color(0x2A1A1A));
                g.fillRect(lx - 35 + i * 14, y - 9, 10, 10);
                g.setColor(i < lives ? C_RED.brighter() : new Color(0x3A2A2A));
                g.drawRect(lx - 35 + i * 14, y - 9, 10, 10);
            }

            // Queue
            DiffConfig cfg = DIFF_MAP.get(difficulty);
            String qTxt = "EN COLA: " + npcQueue.size();
            g.setColor(C_TEXT_DIM); g.setFont(FONT_PIXEL_SM);
            g.drawString(qTxt, w / 2 - 40, y);
        }

        void hudItem(Graphics2D g, int x, int y, String label, String val) {
            g.setColor(C_TEXT_DIM); g.setFont(FONT_PIXEL_SM);
            g.drawString(label, x, y);
            g.setColor(C_ACCENT); g.setFont(FONT_PIXEL);
            g.drawString(val, x + g.getFontMetrics(FONT_PIXEL_SM).stringWidth(label) + 6, y);
        }

        // ── Scene ─────────────────────────────────────────────────────────────
        void drawScene(Graphics2D g, int w, int h) {
            int top = HUD_H, bottom = h - BAR_H;
            int sceneH = bottom - top;

            // Background — brick-like wall pattern
            g.setColor(C_WALL);
            g.fillRect(0, top, w, sceneH);
            // brick pattern
            g.setColor(new Color(0x222018));
            int brickW = 32, brickH = 12;
            for (int row = 0; row * brickH < sceneH; row++) {
                int offX = (row % 2 == 0) ? 0 : brickW / 2;
                for (int col = -1; col * brickW < w + brickW; col++) {
                    g.drawRect(col * brickW + offX, top + row * brickH, brickW - 1, brickH - 1);
                }
            }

            // Floor
            int floorY = bottom - 60;
            g.setColor(C_FLOOR);
            g.fillRect(0, floorY, w, 60);
            // floor tiles
            g.setColor(new Color(0x3A2C1C));
            for (int x = 0; x < w; x += 40) g.drawLine(x, floorY, x, bottom);
            g.drawLine(0, floorY + 1, w, floorY + 1);

            // Desk (booth)
            int deskW = 220, deskH = 52;
            int deskX = w / 2 - deskW / 2, deskY = bottom - deskH;
            drawDesk(g, deskX, deskY, deskW, deskH);

            // Document on desk
            if (toolsActive) {
                drawDocOnDesk(g, w / 2 - 60, deskY - 72);
            }

            // NPC
            if (currentNpc != null) {
                boolean speechVisible = speechText != null &&
                    System.currentTimeMillis() - speechStart < 3500;
                drawNpc(g, currentNpc, floorY, speechVisible);
                if (speechVisible) repaint();
            }
        }

        void drawDesk(Graphics2D g, int x, int y, int w, int h) {
            // desk top highlight
            g.setColor(new Color(0x5A4228));
            g.fillRect(x - 4, y - 4, w + 8, 8);
            // main desk body
            g.setColor(C_DESK);
            g.fillRect(x, y, w, h);
            g.setColor(C_BORDER);
            g.drawRect(x, y, w, h);
            // wood grain pixels
            g.setColor(new Color(0x2A1E0C));
            g.drawLine(x + 10, y + 12, x + w - 10, y + 12);
            g.drawLine(x + 10, y + 24, x + w - 10, y + 24);
            g.drawLine(x + 10, y + 36, x + w - 10, y + 36);
        }

        void drawDocOnDesk(Graphics2D g, int x, int y) {
            // Paper on desk
            g.setColor(C_PAPER);
            g.fillRect(x, y, 80, 60);
            g.setColor(C_PAPER_DK);
            g.drawRect(x, y, 80, 60);
            // pixel paper lines
            g.setColor(C_PAPER_DK);
            for (int i = 1; i < 5; i++) g.drawLine(x + 8, y + i * 10, x + 72, y + i * 10);
            // icon
            g.setColor(C_ACCENT);
            g.setFont(FONT_PIXEL_SM);
            g.drawString("[DOC]", x + 14, y + 36);
            g.setColor(C_TEXT_DIM);
            g.setFont(new Font("Courier New", Font.PLAIN, 9));
            g.drawString("clic para ver", x + 6, y + 54);
        }

        void drawNpc(Graphics2D g, Npc npc, int floorY, boolean speech) {
            int nx = (int) npc.x;
            int bodyH = 180, bodyW = 56;
            int headD = 40;
            int legH = 34;

            int bodyY = floorY - bodyH;
            int headX = nx + bodyW / 2 - headD / 2;
            int headY = bodyY;

            // Shadow
            g.setColor(new Color(0, 0, 0, 60));
            g.fillOval(nx - 8, floorY - 6, bodyW + 16, 12);

            // Legs (pixel blocks)
            g.setColor(npc.leg);
            g.fillRect(nx + 6,  floorY - legH, 18, legH);
            g.fillRect(nx + 32, floorY - legH, 18, legH);
            g.setColor(npc.leg.darker());
            g.drawRect(nx + 6,  floorY - legH, 18, legH);
            g.drawRect(nx + 32, floorY - legH, 18, legH);

            // Torso
            int torsoH = bodyH - headD - legH - 4;
            g.setColor(npc.torso);
            g.fillRect(nx, floorY - legH - torsoH, bodyW, torsoH);
            g.setColor(npc.torso.darker());
            g.drawRect(nx, floorY - legH - torsoH, bodyW, torsoH);
            // collar / shirt detail
            g.setColor(npc.torso.brighter());
            g.fillRect(nx + 20, floorY - legH - torsoH + 8, 16, 4);

            // Head (pixel block, not round)
            int hy = floorY - legH - torsoH - headD - 2;
            g.setColor(npc.skin);
            g.fillRect(nx + 8, hy, headD, headD);
            g.setColor(npc.skin.darker());
            g.drawRect(nx + 8, hy, headD, headD);

            // Eyes (2×2 pixel)
            g.setColor(C_INK);
            g.fillRect(nx + 15, hy + 12, 5, 5);
            g.fillRect(nx + 35, hy + 12, 5, 5);
            // Mouth
            g.fillRect(nx + 20, hy + 26, 16, 3);

            // Hat (simple pixel block)
            g.setColor(npc.torso.darker().darker());
            g.fillRect(nx + 4, hy - 10, headD + 8, 10);
            g.fillRect(nx + 10, hy - 18, headD - 4, 10);

            // Walk bob
            if (npc.walking) {
                int bob = (int)(Math.sin(System.currentTimeMillis() * 0.02) * 3);
                g.translate(0, bob);
            }

            // Speech bubble
            if (speech && speechText != null) {
                int bx = nx - 30, by2 = hy - 56;
                int bw = 140, bh = 36;
                g.setTransform(new java.awt.geom.AffineTransform()); // reset transform
                g.setColor(C_PAPER);
                g.fillRect(bx, by2, bw, bh);
                g.setColor(C_PAPER_DK);
                g.drawRect(bx, by2, bw, bh);
                // tail
                int[] px = {bx + 30, bx + 40, bx + 30}; int[] py = {by2 + bh, by2 + bh + 10, by2 + bh};
                g.setColor(C_PAPER); g.fillPolygon(px, py, 3);
                g.setColor(C_PAPER_DK); g.drawLine(bx + 30, by2 + bh, bx + 40, by2 + bh + 10);
                g.drawLine(bx + 40, by2 + bh + 10, bx + 50, by2 + bh);
                g.setColor(C_INK); g.setFont(new Font("Courier New", Font.PLAIN, 10));
                // clip text
                String st = speechText.length() > 22 ? speechText.substring(0, 22) + "..." : speechText;
                g.drawString(st, bx + 8, by2 + 22);
            } else {
                g.setTransform(new java.awt.geom.AffineTransform()); // reset transform
            }

            // Name tag
            if (npc.arrived) {
                g.setFont(FONT_PIXEL_SM);
                FontMetrics fm = g.getFontMetrics();
                int tw = fm.stringWidth(npc.name);
                g.setColor(new Color(0, 0, 0, 140));
                g.fillRect(nx + bodyW / 2 - tw / 2 - 4, floorY + 4, tw + 8, 14);
                g.setColor(C_ACCENT);
                g.drawString(npc.name, nx + bodyW / 2 - tw / 2, floorY + 15);
            }
        }

        // ── Toolbar ───────────────────────────────────────────────────────────
        void drawToolbar(Graphics2D g, int w, int h) {
            int by = h - BAR_H;
            g.setColor(C_BG);
            g.fillRect(0, by, w, BAR_H);
            g.setColor(C_BORDER);
            g.drawLine(0, by, w, by);

            Rectangle[] areas = getToolAreas(w, h);

            // Approve
            drawToolBtn(g, areas[0], "✓", "APROBAR",
                toolsActive ? C_GREEN_ST : C_PANEL,
                toolsActive ? new Color(0x1A7A2A) : C_BORDER,
                hoverApprove && toolsActive);

            // Deny
            drawToolBtn(g, areas[1], "✗", "DENEGAR",
                toolsActive ? C_RED : C_PANEL,
                toolsActive ? new Color(0x8B1A1A) : C_BORDER,
                hoverDeny && toolsActive);

            // Doc
            drawToolBtn(g, areas[2], "≡", "PASAPORTE",
                toolsActive ? C_PANEL : new Color(0x111008),
                toolsActive ? C_ACCENT : C_BORDER,
                hoverDoc && toolsActive);

            // Agenda
            drawToolBtn(g, areas[3], "☰", "AGENDA", C_PANEL, C_BORDER, hoverAgenda);
        }

        void drawToolBtn(Graphics2D g, Rectangle r, String icon, String label,
                         Color bg, Color border, boolean hover) {
            Color fill = hover ? bg.brighter() : bg;
            g.setColor(fill);
            g.fillRect(r.x, r.y, r.width, r.height);
            g.setColor(border);
            g.drawRect(r.x, r.y, r.width, r.height);
            // pixel shadow
            g.setColor(C_BG);
            g.drawLine(r.x + r.width + 1, r.y + 2, r.x + r.width + 1, r.y + r.height + 1);
            g.drawLine(r.x + 2, r.y + r.height + 1, r.x + r.width + 1, r.y + r.height + 1);

            g.setColor(hover ? Color.WHITE : (border.equals(C_BORDER) ? C_TEXT_DIM : C_PAPER));
            g.setFont(new Font("Courier New", Font.BOLD, 22));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(icon, r.x + (r.width - fm.stringWidth(icon)) / 2, r.y + 36);
            g.setFont(new Font("Courier New", Font.BOLD, 8));
            fm = g.getFontMetrics();
            g.setColor(C_TEXT_DIM);
            g.drawString(label, r.x + (r.width - fm.stringWidth(label)) / 2, r.y + r.height - 6);
        }

        // ── Stamp flash ───────────────────────────────────────────────────────
        void drawStampFlash(Graphics2D g, int w, int h, float alpha) {
            Color bg = stampFlash.equals("APROBADO")
                ? new Color(0, 60, 0, (int)(50 * alpha))
                : new Color(100, 0, 0, (int)(50 * alpha));
            g.setColor(bg);
            g.fillRect(0, 0, w, h);

            g.setFont(new Font("Courier New", Font.BOLD, 72));
            FontMetrics fm = g.getFontMetrics();
            int sw = fm.stringWidth(stampFlash);
            int sx = (w - sw) / 2, sy = h / 2 + 24;

            Color fc = stampFlash.equals("APROBADO")
                ? new Color(0, 180, 40, (int)(220 * alpha))
                : new Color(220, 30, 0, (int)(220 * alpha));
            // border stamp style
            g.setColor(fc);
            g.drawRect(sx - 20, sy - 72, sw + 40, 86);
            g.drawRect(sx - 24, sy - 76, sw + 48, 94);
            g.drawString(stampFlash, sx, sy);
        }

        // ── Document modal ────────────────────────────────────────────────────
        void drawDocModal(Graphics2D g, int w, int h) {
            // dim background
            g.setColor(new Color(0, 0, 0, 160));
            g.fillRect(0, 0, w, h);

            Npc npc = currentNpc;
            if (npc == null) return;
            NpcDoc doc = npc.doc;

            int mw = 460, mh = 420;
            int mx = w / 2 - mw / 2, my = h / 2 - mh / 2;

            // Paper background with slight tilt — use paper color
            g.setColor(C_PAPER);
            g.fillRect(mx + 2, my + 2, mw, mh); // shadow
            g.setColor(C_PAPER);
            g.fillRect(mx, my, mw, mh);
            g.setColor(C_PAPER_DK);
            g.drawRect(mx, my, mw, mh);
            g.drawRect(mx - 2, my - 2, mw + 4, mh + 4);

            // Stamp watermark
            g.setFont(new Font("Courier New", Font.BOLD, 60));
            g.setColor(new Color(0x8B1010, true));
            Composite old = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.08f));
            g.drawString("ARSTOTZKA", mx + mw / 2 - 180, my + mh / 2 + 20);
            g.setComposite(old);

            // Header
            g.setColor(C_INK);
            g.drawLine(mx + 16, my + 70, mx + mw - 16, my + 70);

            // Country + type
            g.setFont(new Font("Courier New", Font.BOLD, 9));
            g.setColor(new Color(0x5A4030));
            g.drawString(doc.country().toUpperCase(), mx + 16, my + 24);
            g.setFont(new Font("Courier New", Font.BOLD, 18));
            g.setColor(C_INK);
            g.drawString("Pasaporte Oficial", mx + 16, my + 52);

            // Photo placeholder
            g.setColor(new Color(0xC8B890));
            g.fillRect(mx + mw - 100, my + 14, 72, 52);
            g.setColor(C_PAPER_DK);
            g.drawRect(mx + mw - 100, my + 14, 72, 52);
            g.setColor(new Color(0x8A7860));
            g.setFont(new Font("Courier New", Font.PLAIN, 28));
            g.drawString(npc.gender.equals("M") ? "♂" : "♀", mx + mw - 80, my + 50);

            // Fields grid
            String[][] fields = {
                {"NOMBRE", doc.name()},
                {"PAÍS", doc.country()},
                {"OCUPACIÓN", doc.occupation()},
                {"AÑO NAC.", String.valueOf(doc.birthYear())},
                {"EXPIRA", String.valueOf(doc.expYear())},
                {"GÉNERO", npc.gender.equals("M") ? "Masculino" : "Femenino"}
            };
            int col1 = mx + 16, col2 = mx + mw / 2 + 8;
            for (int i = 0; i < fields.length; i++) {
                int fx = (i % 2 == 0) ? col1 : col2;
                int fy = my + 90 + (i / 2) * 52;
                g.setColor(C_PAPER_DK);
                g.drawLine(fx, fy + 20, fx + 190, fy + 20);
                g.setFont(new Font("Courier New", Font.PLAIN, 8));
                g.setColor(new Color(0x8A7060));
                g.drawString(fields[i][0], fx, fy + 10);
                g.setFont(new Font("Courier New", Font.BOLD, 12));
                g.setColor(C_INK);
                g.drawString(fields[i][1], fx, fy + 26);
            }

            // Interrogation section
            int qy = my + 260;
            g.setColor(C_PAPER_DK);
            g.drawLine(mx + 16, qy, mx + mw - 16, qy);
            g.setFont(new Font("Courier New", Font.PLAIN, 9));
            g.setColor(new Color(0x8A7060));
            g.drawString("INTERROGAR", mx + 16, qy + 14);

            // Question buttons
            String[] questions = {"¿Cuál es tu nombre?", "¿De dónde vienes?", "¿Cuál es tu ocupación?", "¿Cuándo vence tu pasaporte?"};
            for (int i = 0; i < 4; i++) {
                int qbx = mx + 16 + (i % 2) * 218;
                int qby = qy + 22 + (i / 2) * 26;
                g.setColor(C_INK);
                g.fillRect(qbx, qby, 205, 20);
                g.setFont(new Font("Courier New", Font.BOLD, 9));
                g.setColor(C_PAPER);
                g.drawString(questions[i], qbx + 6, qby + 14);
            }

            // Answer box
            int ay = qy + 82;
            g.setColor(new Color(0xE8D8B0));
            g.fillRect(mx + 16, ay, mw - 32, 28);
            g.setColor(C_ACCENT);
            g.fillRect(mx + 16, ay, 3, 28);
            g.setFont(new Font("Courier New", Font.PLAIN, 11));
            g.setColor(C_INK);
            if (!docAnswer.isEmpty()) g.drawString("› " + docAnswer, mx + 24, ay + 18);

            // Close btn
            g.setColor(C_INK);
            g.setFont(new Font("Courier New", Font.BOLD, 16));
            g.drawString("✕", mx + mw - 22, my + 20);
        }

        // ── Agenda ────────────────────────────────────────────────────────────
        void drawAgenda(Graphics2D g, int w, int h) {
            g.setColor(new Color(0, 0, 0, 160));
            g.fillRect(0, 0, w, h);

            int mw = 380, mh = 340;
            int mx = w / 2 - mw / 2, my = h / 2 - mh / 2;

            g.setColor(new Color(0x1E1810));
            g.fillRect(mx, my, mw, mh);
            g.setColor(C_BORDER);
            g.drawRect(mx, my, mw, mh);

            g.setColor(C_ACCENT);
            g.setFont(FONT_PIXEL_LG);
            g.drawString("AGENDA OFICIAL", mx + 16, my + 32);
            g.setColor(C_BORDER);
            g.drawLine(mx + 16, my + 40, mx + mw - 16, my + 40);

            String[] rules = {
                "El pasaporte debe estar vigente (no vencido).",
                "El nombre debe coincidir con el declarado.",
                "La ocupación debe coincidir en el documento.",
                "Solo se aceptan países permitidos hoy.",
                "Inconsistencias deben ser rechazadas."
            };
            String[] flags = {
                "Nerviosismo al responder preguntas.",
                "País diferente al documento.",
                "Nombre o apellido inconsistente.",
                "Fecha de expiración pasada.",
                "Ocupación que no coincide."
            };

            g.setFont(FONT_PIXEL_SM);
            g.setColor(C_TEXT_DIM);
            g.drawString("REQUISITOS DE ENTRADA", mx + 16, my + 58);
            for (int i = 0; i < rules.length; i++) {
                g.setColor(C_ACCENT); g.drawString("›", mx + 16, my + 74 + i * 16);
                g.setColor(C_PAPER); g.setFont(new Font("Courier New", Font.PLAIN, 10));
                g.drawString(rules[i], mx + 28, my + 74 + i * 16);
            }

            g.setFont(FONT_PIXEL_SM);
            g.setColor(C_TEXT_DIM);
            g.drawString("SEÑALES DE ALERTA", mx + 16, my + 164);
            for (int i = 0; i < flags.length; i++) {
                g.setColor(C_RED); g.drawString("!", mx + 16, my + 180 + i * 16);
                g.setColor(C_PAPER); g.setFont(new Font("Courier New", Font.PLAIN, 10));
                g.drawString(flags[i], mx + 28, my + 180 + i * 16);
            }

            g.setFont(FONT_PIXEL_SM);
            g.setColor(C_TEXT_DIM);
            g.drawString("PAÍSES PERMITIDOS HOY:", mx + 16, my + 268);
            g.setColor(C_ACCENT);
            g.setFont(new Font("Courier New", Font.BOLD, 11));
            g.drawString(String.join("  ·  ", allowedCountries), mx + 16, my + 284);

            g.setColor(C_TEXT);
            g.setFont(new Font("Courier New", Font.BOLD, 14));
            g.drawString("✕", mx + mw - 24, my + 22);
        }

        // ── Result overlay ────────────────────────────────────────────────────
        void drawResult(Graphics2D g, int w, int h) {
            g.setColor(new Color(0, 0, 0, 210));
            g.fillRect(0, 0, w, h);

            int bw = 460, bh = 280;
            int bx = w / 2 - bw / 2, by = h / 2 - bh / 2;

            g.setColor(C_PANEL);
            g.fillRect(bx, by, bw, bh);
            g.setColor(C_BORDER);
            g.drawRect(bx, by, bw, bh);
            g.setColor(C_ACCENT);
            g.drawRect(bx - 3, by - 3, bw + 6, bh + 6);

            // Icon
            g.setFont(new Font("Courier New", Font.BOLD, 48));
            g.setColor(isGameOver ? C_RED : C_ACCENT);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(resultIcon, bx + bw / 2 - fm.stringWidth(resultIcon) / 2, by + 68);

            // Title
            g.setFont(FONT_PIXEL_LG);
            fm = g.getFontMetrics();
            g.setColor(C_ACCENT);
            g.drawString(resultTitle, bx + bw / 2 - fm.stringWidth(resultTitle) / 2, by + 108);

            // Message
            g.setFont(new Font("Courier New", Font.PLAIN, 12));
            g.setColor(C_TEXT);
            fm = g.getFontMetrics();
            g.drawString(resultMsg, bx + bw / 2 - fm.stringWidth(resultMsg) / 2, by + 140);

            // Score
            g.setFont(FONT_PIXEL);
            g.setColor(C_PAPER);
            fm = g.getFontMetrics();
            g.drawString(resultScore, bx + bw / 2 - fm.stringWidth(resultScore) / 2, by + 172);

            // Buttons
            int btnW = 160, btnH = 36;
            int b1x = bx + bw / 2 - btnW - 10, b2x = bx + bw / 2 + 10;
            int bby = by + bh - 60;
            drawModalBtn(g, b1x, bby, btnW, btnH, isGameOver ? "MENU" : "CONTINUAR", C_RED);
            if (!isGameOver) drawModalBtn(g, b2x, bby, btnW, btnH, "MENU", C_PANEL);
        }

        void drawModalBtn(Graphics2D g, int x, int y, int w, int h, String label, Color bg) {
            g.setColor(bg);
            g.fillRect(x, y, w, h);
            g.setColor(C_ACCENT);
            g.drawRect(x, y, w, h);
            g.setFont(FONT_PIXEL);
            FontMetrics fm = g.getFontMetrics();
            g.setColor(C_PAPER);
            g.drawString(label, x + (w - fm.stringWidth(label)) / 2, y + h / 2 + 5);
        }

        // ── Mouse events ──────────────────────────────────────────────────────
        @Override public void mouseClicked(MouseEvent e) {
            int w = getWidth(), h = getHeight();

            // Result overlay buttons
            if (resultOpen) {
                int bw = 160, bh = 36;
                int bx = w / 2 - 460 / 2, by = h / 2 - 280 / 2;
                int bby = by + 280 - 60;
                Rectangle btn1 = new Rectangle(bx + 460/2 - bw - 10, bby, bw, bh);
                Rectangle btn2 = new Rectangle(bx + 460/2 + 10, bby, bw, bh);
                if (btn1.contains(e.getPoint())) continueGame();
                else if (!isGameOver && btn2.contains(e.getPoint())) { resultOpen = false; showCard(CARD_INTRO); repaint(); }
                return;
            }

            // Agenda close / click
            if (agendaOpen) {
                int mw = 380, mh = 340;
                int mx = w / 2 - mw / 2, my = h / 2 - mh / 2;
                if (new Rectangle(mx + mw - 32, my + 10, 26, 24).contains(e.getPoint())) {
                    agendaOpen = false; repaint();
                }
                return;
            }

            // Doc modal — question buttons + close
            if (docModalOpen) {
                int mw = 460, mh = 420;
                int mx = w / 2 - mw / 2, my = h / 2 - mh / 2;
                // Close
                if (new Rectangle(mx + mw - 28, my + 8, 24, 20).contains(e.getPoint())) {
                    docModalOpen = false; docAnswer = ""; repaint(); return;
                }
                // Question buttons
                int qy = my + 260;
                String[] questions = {"¿Cuál es tu nombre?", "¿De dónde vienes?", "¿Cuál es tu ocupación?", "¿Cuándo vence tu pasaporte?"};
                Npc npc = currentNpc; if (npc == null) return;
                String[][] answers = buildAnswers(npc);
                for (int i = 0; i < 4; i++) {
                    int qbx = mx + 16 + (i % 2) * 218;
                    int qby = qy + 22 + (i / 2) * 26;
                    if (new Rectangle(qbx, qby, 205, 20).contains(e.getPoint())) {
                        docAnswer = answers[i][1]; repaint(); return;
                    }
                }
                return;
            }

            // Toolbar
            Rectangle[] areas = getToolAreas(w, h);
            if (toolsActive) {
                if (areas[0].contains(e.getPoint())) stamp(true);
                else if (areas[1].contains(e.getPoint())) stamp(false);
                else if (areas[2].contains(e.getPoint())) { docModalOpen = true; docAnswer = ""; repaint(); }
            }
            if (areas[3].contains(e.getPoint())) { agendaOpen = true; repaint(); }

            // Click on document on desk
            if (toolsActive && currentNpc != null) {
                int deskW = 220, deskH = 52;
                int floorY = h - BAR_H - 60;
                int deskY = h - BAR_H - deskH;
                int docX = w / 2 - 60, docY = deskY - 72;
                if (new Rectangle(docX, docY, 80, 60).contains(e.getPoint())) {
                    docModalOpen = true; docAnswer = ""; repaint();
                }
            }
        }

        String[][] buildAnswers(Npc npc) {
            NpcDoc doc = npc.doc;
            boolean fakeCountry = npc.isFake && npc.fakeReason != null && npc.fakeReason.contains("País");
            boolean fakeName    = npc.isFake && npc.fakeReason != null && npc.fakeReason.contains("Nombre");
            boolean fakeOcc     = npc.isFake && npc.fakeReason != null && npc.fakeReason.contains("Ocupación");
            boolean fakeExp     = npc.isFake && npc.fakeReason != null && npc.fakeReason.contains("vencido");

            return new String[][]{
                {"¿Cuál es tu nombre?",       fakeName    ? "Soy " + doc.name() + "... digo, " + npc.name + "." : "Me llamo " + npc.name + "."},
                {"¿De dónde vienes?",         fakeCountry ? "Eh... de " + npc.realCountry + ". ¡De allí vengo!" : "Vengo de " + npc.realCountry + "."},
                {"¿Cuál es tu ocupación?",    fakeOcc     ? "Soy... " + npc.occupation + ". Sí, eso." : "Soy " + npc.occupation + "."},
                {"¿Cuándo vence tu pasaporte?", fakeExp   ? "Vence... próximamente. Está vigente." : "Vence en " + npc.expYear + "."}
            };
        }

        @Override public void mousePressed(MouseEvent e) {}
        @Override public void mouseReleased(MouseEvent e) {}
        @Override public void mouseEntered(MouseEvent e) {}
        @Override public void mouseExited(MouseEvent e) {}
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  HELPER COMPONENTS
    // ═══════════════════════════════════════════════════════════════════════

    /** Pixel-art flag — drawn purely with rectangles */
    static class FlagPixelPanel extends JPanel {
        FlagPixelPanel() { setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            int w = getWidth(), h = getHeight();
            // Red border / background
            g.setColor(new Color(0x6B1010));
            g.fillRect(0, 0, w, h);
            // Three horizontal stripes
            g.setColor(new Color(0xC8A000));
            g.fillRect(4, 4, w - 8, h / 3 - 4);
            g.setColor(new Color(0x1A3A7A));
            g.fillRect(4, h / 3 + 1, w - 8, h / 3 - 2);
            g.setColor(new Color(0x6B1010));
            g.fillRect(4, 2 * h / 3 + 1, w - 8, h - 2 * h / 3 - 5);
            // Stars (pixel blocks)
            g.setColor(Color.WHITE);
            int[] xs = {w/2 - 20, w/2, w/2 + 20};
            for (int x : xs) {
                g.fillRect(x - 3, h / 2 - 3, 6, 6);
                g.fillRect(x - 1, h / 2 - 5, 2, 2);
                g.fillRect(x - 1, h / 2 + 3, 2, 2);
                g.fillRect(x - 5, h / 2 - 1, 2, 2);
                g.fillRect(x + 3, h / 2 - 1, 2, 2);
            }
            // Border
            g.setColor(new Color(0x8B6914));
            g.drawRect(0, 0, w - 1, h - 1);
            g.drawRect(1, 1, w - 3, h - 3);
        }
    }

    /** Retro pixel button */
    static class PixelButton extends JButton {
        Color bg, fg;
        boolean hov;
        PixelButton(String text, Color bg, Color fg) {
            super(text); this.bg = bg; this.fg = fg;
            setOpaque(false); setContentAreaFilled(false); setBorderPainted(false);
            setFocusPainted(false);
            setFont(new Font("Courier New", Font.BOLD, 16));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hov = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hov = false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int w = getWidth(), h = getHeight();
            Color fill = hov ? bg.brighter() : bg;
            g2.setColor(fill); g2.fillRect(0, 0, w, h);
            g2.setColor(C_ACCENT); g2.drawRect(0, 0, w - 1, h - 1);
            // pixel shadow
            g2.setColor(C_BG);
            g2.drawLine(w, 2, w, h); g2.drawLine(2, h, w, h);
            g2.setColor(fg); g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(), (w - fm.stringWidth(getText())) / 2, (h + fm.getAscent() - fm.getDescent()) / 2);
        }
    }

    /** Difficulty selection card */
    static class DiffCard extends JPanel {
        String title; String[] lines; Color topColor;
        boolean hov;
        DiffCard(String title, String desc, Color topColor) {
            this.title = title; this.topColor = topColor;
            this.lines = desc.split("\n");
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hov = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hov = false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g) {
            int w = getWidth(), h = getHeight();
            Graphics2D g2 = (Graphics2D) g;
            g2.setColor(hov ? new Color(0x28201A) : C_PANEL);
            g2.fillRect(0, 0, w, h);
            g2.setColor(topColor);
            g2.fillRect(0, 0, w, 5);
            g2.setColor(hov ? C_ACCENT : C_BORDER);
            g2.drawRect(0, 0, w - 1, h - 1);
            g2.setFont(new Font("Courier New", Font.BOLD, 16));
            g2.setColor(C_PAPER);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(title, (w - fm.stringWidth(title)) / 2, 32);
            g2.setFont(new Font("Courier New", Font.PLAIN, 10));
            g2.setColor(C_TEXT);
            for (int i = 0; i < lines.length; i++) {
                fm = g2.getFontMetrics();
                g2.drawString(lines[i], (w - fm.stringWidth(lines[i])) / 2, 56 + i * 18);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  MAIN
    // ═══════════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        // Attempt pixel-perfect rendering
        System.setProperty("awt.useSystemAAFontSettings", "off");
        System.setProperty("swing.aatext", "false");
        SwingUtilities.invokeLater(PapersPlease::new);
    }
}
