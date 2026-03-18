package com.carpark.ui;

import com.carpark.model.Car;
import com.carpark.model.SimulationMetrics;
import com.carpark.sync.ParkingLot;
import com.carpark.thread.CarProducer;
import com.carpark.thread.ParkingGuide;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.scene.effect.BoxBlur;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Car Park Management Simulation — JavaFX application.
 * <p>
 * Renders an <b>isometric nighttime parking lot</b> on a Canvas with
 * realistic car models, street-lamp glows, building silhouettes, and
 * smooth spawn/despawn animations. Producer/Consumer threads drive
 * the simulation using Semaphores and Condition Variables.
 * </p>
 *
 * @author vattanac
 */
public class CarParkSimApp extends Application {

    // ── Defaults ────────────────────────────────────────────
    private static final int DEF_CAP = 10, DEF_PROD = 3, DEF_CONS = 2;
    private static final int DEF_PROD_MS = 1500, DEF_CONS_MS = 800, DEF_PROC_MS = 1000;

    // ── Isometric helpers ───────────────────────────────────
    // Isometric projection: rotate 45° around Y then ~30° tilt
    private static final double ISO_COS = Math.cos(Math.toRadians(30));
    private static final double ISO_SIN = Math.sin(Math.toRadians(30));
    private static final double TILE = 38; // base tile size
    private double CX, CY; // canvas center offset

    /** Convert 3D world (x,y,z) → 2D screen (px, py). */
    private double isoX(double x, double y, double z) { return CX + (x - z) * TILE * 0.87; }
    private double isoY(double x, double y, double z) { return CY + (x + z) * TILE * 0.5 - y * TILE * 1.0; }

    // ── Car colours ─────────────────────────────────────────
    private static final Color[] CAR_COLS = {
        Color.web("#2266DD"), Color.web("#DD2244"), Color.web("#22BB55"),
        Color.web("#DD9911"), Color.web("#7733CC"), Color.web("#22BBBB"),
        Color.web("#CC2288"), Color.web("#44CC88"),
    };
    private static final Color[] CAR_DARK = {
        Color.web("#1144AA"), Color.web("#AA1133"), Color.web("#118833"),
        Color.web("#AA7700"), Color.web("#5522AA"), Color.web("#118888"),
        Color.web("#991166"), Color.web("#228855"),
    };

    // ── Simulation ──────────────────────────────────────────
    private ParkingLot parkingLot;
    private SimulationMetrics metrics;
    private final List<CarProducer> producers   = new ArrayList<>();
    private final List<ParkingGuide> consumers  = new ArrayList<>();
    private final List<Thread> prodThreads = new ArrayList<>();
    private final List<Thread> consThreads = new ArrayList<>();
    private boolean running = false;

    // ── Slot animation ──────────────────────────────────────
    private static final int MAX_SLOTS = 18;
    private final double[] slotScale  = new double[MAX_SLOTS]; // 0..1
    private final int[]    slotTarget = new int[MAX_SLOTS];    // 0 or 1
    private final int[]    slotColor  = new int[MAX_SLOTS];
    private final double[] slotBounce = new double[MAX_SLOTS]; // bounce anim

    // ── Canvas ──────────────────────────────────────────────
    private Canvas canvas;
    private static final double CW = 760, CH = 720;

    // ── UI ───────────────────────────────────────────────────
    private Label lblOcc, lblTp, lblWt, lblProd, lblCons, lblElapsed;
    private VBox threadBox;
    private Slider slCap, slProdR, slConsR, slProcT;
    private Label cvCap, cvProd, cvCons, cvProc;
    private Button btnStart, btnStop, btnReset;
    private TextArea logArea;

    private long frameCount = 0;

    // ══════════════════════════════════════════════════════════

    @Override
    public void start(Stage stage) {
        stage.setTitle("Car Park Management Sim");

        CX = CW / 2;
        CY = CH * 0.38;

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color:#080818;");

        canvas = new Canvas(CW, CH);
        StackPane cp = new StackPane(canvas);
        cp.setStyle("-fx-background-color:#080818;");
        root.setCenter(cp);
        root.setRight(buildPanel());

        Scene scene = new Scene(root, 1100, 720);
        stage.setScene(scene);
        stage.setMinWidth(950); stage.setMinHeight(600);
        stage.setOnCloseRequest(e -> stopSim());
        stage.show();

        drawFrame(canvas.getGraphicsContext2D(), DEF_CAP);
        startTimer();
    }

    @Override public void stop() { stopSim(); }

    // ══════════════════════════════════════════════════════════
    //  ISOMETRIC SCENE RENDERER
    // ══════════════════════════════════════════════════════════

    private void drawFrame(GraphicsContext gc, int cap) {
        frameCount++;
        double w = CW, h = CH;
        double time = frameCount * 0.02;

        // ── Sky ──
        gc.setFill(new LinearGradient(0,0,0,1,true,CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#030510")),
            new Stop(0.4, Color.web("#080818")),
            new Stop(1, Color.web("#0b0f22"))));
        gc.fillRect(0, 0, w, h);

        // ── Stars ──
        Random rng = new Random(123);
        for (int i = 0; i < 120; i++) {
            double sx = rng.nextDouble() * w, sy = rng.nextDouble() * h * 0.5;
            double twinkle = 0.3 + 0.7 * (0.5 + 0.5 * Math.sin(time * 2 + i * 0.7));
            gc.setFill(Color.web("#aabbee", twinkle * 0.7));
            double sz = 0.8 + rng.nextDouble() * 1.2;
            gc.fillOval(sx, sy, sz, sz);
        }

        // ── Moon ──
        double mx = w - 120, my = 50;
        gc.setFill(Color.web("#ddeeff", 0.04));
        gc.fillOval(mx - 30, my - 30, 80, 80); // outer glow
        gc.setFill(Color.web("#ddeeff", 0.08));
        gc.fillOval(mx - 15, my - 15, 50, 50); // mid glow
        gc.setFill(Color.web("#ddeeff", 0.85));
        gc.fillOval(mx, my, 22, 22); // moon disc

        // ── Ground plane (isometric diamond) ──
        double gs = 9; // ground half-size in world units
        double[] gx = { isoX(-gs,0,0), isoX(0,0,-gs), isoX(gs,0,0), isoX(0,0,gs) };
        double[] gy = { isoY(-gs,0,0), isoY(0,0,-gs), isoY(gs,0,0), isoY(0,0,gs) };
        gc.setFill(Color.web("#0c1a0c"));
        gc.fillPolygon(gx, gy, 4);

        // ── Asphalt lot ──
        double ls = 7;
        double[] lx = { isoX(-ls,0.01,-ls), isoX(ls,0.01,-ls), isoX(ls,0.01,ls), isoX(-ls,0.01,ls) };
        double[] ly = { isoY(-ls,0.01,-ls), isoY(ls,0.01,-ls), isoY(ls,0.01,ls), isoY(-ls,0.01,ls) };
        gc.setFill(Color.web("#1a1a2a"));
        gc.fillPolygon(lx, ly, 4);

        // Lot border lines
        gc.setStroke(Color.web("#333355", 0.6));
        gc.setLineWidth(1.5);
        gc.strokePolygon(lx, ly, 4);

        // ── Building (back wall, isometric) ──
        drawIsoBox(gc, 0, 2.5, -7.5, 12, 5, 1.5, Color.web("#1e2838"), Color.web("#161e2c"), Color.web("#141a26"));
        // Roof
        drawIsoBox(gc, 0, 5.1, -7.5, 12.4, 0.2, 1.8, Color.web("#141a26"), Color.web("#0f1520"), Color.web("#0d121c"));
        // Windows on front face
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 7; c++) {
                boolean lit = Math.sin(c * 3.5 + r * 2.1) > -0.2;
                double wx = -5 + c * 1.7;
                double wy = 1.2 + r * 2.2;
                double wz = -6.7;
                Color wc = lit ? Color.web("#ffdd88", 0.75) : Color.web("#0a0f18", 0.8);
                drawIsoBox(gc, wx, wy, wz, 0.8, 1.0, 0.05, wc, wc, wc);
            }
        }

        // ── Sign ──
        drawIsoBox(gc, 0, 5.5, -6.8, 3, 0.6, 0.1, Color.web("#1144cc"), Color.web("#0d33aa"), Color.web("#0a2288"));

        // ── Street lamps ──
        drawLamp(gc, -7, 0, -7, time);
        drawLamp(gc, 7, 0, -7, time);
        drawLamp(gc, -7, 0, 5, time);
        drawLamp(gc, 7, 0, 5, time);

        // ── Road (extending from lot) ──
        double[] rx = { isoX(-1.5,0.01,7), isoX(1.5,0.01,7), isoX(1.5,0.01,14), isoX(-1.5,0.01,14) };
        double[] ry = { isoY(-1.5,0.01,7), isoY(1.5,0.01,7), isoY(1.5,0.01,14), isoY(-1.5,0.01,14) };
        gc.setFill(Color.web("#161620"));
        gc.fillPolygon(rx, ry, 4);
        // Center dashes
        for (int i = 0; i < 4; i++) {
            double dz = 7.5 + i * 1.6;
            double[] dx = { isoX(-0.05,0.02,dz), isoX(0.05,0.02,dz), isoX(0.05,0.02,dz+0.8), isoX(-0.05,0.02,dz+0.8) };
            double[] dy = { isoY(-0.05,0.02,dz), isoY(0.05,0.02,dz), isoY(0.05,0.02,dz+0.8), isoY(-0.05,0.02,dz+0.8) };
            gc.setFill(Color.web("#ccaa00", 0.6));
            gc.fillPolygon(dx, dy, 4);
        }

        // ── Trees ──
        drawTree(gc, -8.5, 0, -6);
        drawTree(gc, 8.5, 0, -6);
        drawTree(gc, -8.5, 0, 5);
        drawTree(gc, 8.5, 0, 5);

        // ── Parking spot lines + cars ──
        int cols = (cap <= 5) ? cap : (cap <= 10) ? 5 : 6;
        int rows = (int) Math.ceil(cap / (double) cols);
        double spotW = 2.2, spotD = 3.2;
        double totalW = cols * spotW, totalD = rows * spotD;
        double startX = -totalW / 2, startZ = -totalD / 2 - 0.5;

        for (int i = 0; i < cap; i++) {
            int c = i % cols, r = i / cols;
            double sx = startX + c * spotW;
            double sz = startZ + r * spotD;

            // Parking lines
            drawParkingLines(gc, sx, sz, spotW, spotD);

            // Car
            if (slotScale[i] > 0.02) {
                double sc = slotScale[i];
                double bounce = slotBounce[i];
                int ci = slotColor[i] % CAR_COLS.length;
                drawIsoCar(gc, sx + spotW / 2, bounce * 0.15, sz + spotD / 2, sc, ci);
            } else {
                // Slot number
                double cx = isoX(sx + spotW / 2, 0, sz + spotD / 2);
                double cy = isoY(sx + spotW / 2, 0, sz + spotD / 2);
                gc.setFill(Color.web("#334455", 0.4));
                gc.setFont(Font.font("Consolas", 8));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText("P" + (i + 1), cx, cy + 3);
            }
        }

        // ── Curb at lot entrance ──
        drawIsoBox(gc, -2, 0.1, 7, 4, 0.15, 0.15, Color.web("#555555"), Color.web("#444444"), Color.web("#333333"));

        // ── Title text ──
        gc.setFill(Color.web("#44aaff", 0.08));
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 32));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("C A R   P A R K", 30, h - 30);

        // ── Counter ──
        int visCount = 0;
        for (int i = 0; i < cap; i++) if (slotScale[i] > 0.5) visCount++;
        gc.setFill(Color.web("#aaccee", 0.5));
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 13));
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.fillText(visCount + " / " + cap, CW - 30, h - 30);
    }

    // ── Isometric box (3 visible faces) ──
    private void drawIsoBox(GraphicsContext gc, double cx, double cy, double cz,
                            double bw, double bh, double bd,
                            Color top, Color front, Color side) {
        double hw = bw/2, hd = bd/2;
        // Top face
        double[] tx = { isoX(cx-hw,cy+bh,cz-hd), isoX(cx+hw,cy+bh,cz-hd),
                        isoX(cx+hw,cy+bh,cz+hd), isoX(cx-hw,cy+bh,cz+hd) };
        double[] ty = { isoY(cx-hw,cy+bh,cz-hd), isoY(cx+hw,cy+bh,cz-hd),
                        isoY(cx+hw,cy+bh,cz+hd), isoY(cx-hw,cy+bh,cz+hd) };
        gc.setFill(top); gc.fillPolygon(tx, ty, 4);

        // Front face (z+hd side)
        double[] fx = { isoX(cx-hw,cy,cz+hd), isoX(cx+hw,cy,cz+hd),
                        isoX(cx+hw,cy+bh,cz+hd), isoX(cx-hw,cy+bh,cz+hd) };
        double[] fy = { isoY(cx-hw,cy,cz+hd), isoY(cx+hw,cy,cz+hd),
                        isoY(cx+hw,cy+bh,cz+hd), isoY(cx-hw,cy+bh,cz+hd) };
        gc.setFill(front); gc.fillPolygon(fx, fy, 4);

        // Right face (x+hw side)
        double[] rx = { isoX(cx+hw,cy,cz-hd), isoX(cx+hw,cy,cz+hd),
                        isoX(cx+hw,cy+bh,cz+hd), isoX(cx+hw,cy+bh,cz-hd) };
        double[] ry = { isoY(cx+hw,cy,cz-hd), isoY(cx+hw,cy,cz+hd),
                        isoY(cx+hw,cy+bh,cz+hd), isoY(cx+hw,cy+bh,cz-hd) };
        gc.setFill(side); gc.fillPolygon(rx, ry, 4);
    }

    // ── Isometric car ──
    private void drawIsoCar(GraphicsContext gc, double wx, double wy, double wz, double scale, int ci) {
        double bw = 1.6 * scale, bh = 0.5 * scale, bd = 2.8 * scale;
        Color body = CAR_COLS[ci];
        Color dark = CAR_DARK[ci];
        Color glass = Color.web("#88ccff", 0.35);

        // Shadow on ground
        gc.setFill(Color.web("#000000", 0.25 * scale));
        double sx = isoX(wx, 0, wz);
        double sy = isoY(wx, 0, wz);
        gc.fillOval(sx - 25 * scale, sy - 8 * scale, 50 * scale, 16 * scale);

        // Car body
        drawIsoBox(gc, wx, wy + 0.15, wz, bw, bh, bd, body, dark, dark.darker());

        // Cabin (glass)
        double cabW = bw * 0.8, cabH = bh * 0.7, cabD = bd * 0.5;
        drawIsoBox(gc, wx, wy + 0.15 + bh, wz - bd * 0.05, cabW, cabH, cabD,
                   glass.brighter(), glass, glass.darker());

        // Roof
        drawIsoBox(gc, wx, wy + 0.15 + bh + cabH, wz - bd * 0.05,
                   cabW * 1.02, bh * 0.12, cabD * 0.95, body, dark, dark.darker());

        // Headlights (front = +z)
        double hlY = wy + 0.35;
        gc.setFill(Color.web("#ffffcc", 0.9));
        gc.fillOval(isoX(wx - bw * 0.35, hlY, wz + bd / 2) - 2, isoY(wx - bw * 0.35, hlY, wz + bd / 2) - 2, 5 * scale, 4 * scale);
        gc.fillOval(isoX(wx + bw * 0.35, hlY, wz + bd / 2) - 2, isoY(wx + bw * 0.35, hlY, wz + bd / 2) - 2, 5 * scale, 4 * scale);

        // Headlight glow cone
        gc.setFill(Color.web("#ffffaa", 0.06 * scale));
        double glx = isoX(wx, hlY, wz + bd / 2 + 1.5);
        double gly = isoY(wx, hlY, wz + bd / 2 + 1.5);
        gc.fillOval(glx - 18 * scale, gly - 6 * scale, 36 * scale, 12 * scale);

        // Tail lights
        gc.setFill(Color.web("#ff2200", 0.8));
        gc.fillOval(isoX(wx - bw * 0.35, hlY, wz - bd / 2) - 1.5, isoY(wx - bw * 0.35, hlY, wz - bd / 2) - 1.5, 4 * scale, 3 * scale);
        gc.fillOval(isoX(wx + bw * 0.35, hlY, wz - bd / 2) - 1.5, isoY(wx + bw * 0.35, hlY, wz - bd / 2) - 1.5, 4 * scale, 3 * scale);

        // Wheels (small dark ovals)
        gc.setFill(Color.web("#111111"));
        double wlY = wy + 0.08;
        double[][] wheelPos = {{wx - bw/2, wlY, wz + bd*0.3}, {wx + bw/2, wlY, wz + bd*0.3},
                               {wx - bw/2, wlY, wz - bd*0.3}, {wx + bw/2, wlY, wz - bd*0.3}};
        for (double[] wp : wheelPos) {
            double px = isoX(wp[0], wp[1], wp[2]);
            double py = isoY(wp[0], wp[1], wp[2]);
            gc.fillOval(px - 3 * scale, py - 2 * scale, 6 * scale, 4 * scale);
        }
    }

    // ── Parking lines ──
    private void drawParkingLines(GraphicsContext gc, double sx, double sz, double sw, double sd) {
        gc.setStroke(Color.web("#ffffff", 0.18));
        gc.setLineWidth(0.8);
        // Left line
        gc.strokeLine(isoX(sx,0.02,sz), isoY(sx,0.02,sz), isoX(sx,0.02,sz+sd), isoY(sx,0.02,sz+sd));
        // Right line
        gc.strokeLine(isoX(sx+sw,0.02,sz), isoY(sx+sw,0.02,sz), isoX(sx+sw,0.02,sz+sd), isoY(sx+sw,0.02,sz+sd));
        // Back line
        gc.strokeLine(isoX(sx,0.02,sz), isoY(sx,0.02,sz), isoX(sx+sw,0.02,sz), isoY(sx+sw,0.02,sz));
    }

    // ── Street lamp ──
    private void drawLamp(GraphicsContext gc, double lx, double ly, double lz, double time) {
        double bx = isoX(lx, 0, lz), by = isoY(lx, 0, lz);
        double tx = isoX(lx, 3.5, lz), ty = isoY(lx, 3.5, lz);

        // Pole
        gc.setStroke(Color.web("#555555"));
        gc.setLineWidth(1.5);
        gc.strokeLine(bx, by, tx, ty);

        // Ground glow
        double flicker = 0.8 + 0.2 * Math.sin(time * 3 + lx * 2);
        gc.setFill(Color.web("#ffaa44", 0.06 * flicker));
        gc.fillOval(bx - 35, by - 12, 70, 24);

        // Bulb
        gc.setFill(Color.web("#ffdd88", 0.9 * flicker));
        gc.fillOval(tx - 3, ty - 3, 6, 6);

        // Bulb glow
        gc.setFill(Color.web("#ffaa44", 0.12 * flicker));
        gc.fillOval(tx - 12, ty - 8, 24, 16);
    }

    // ── Tree ──
    private void drawTree(GraphicsContext gc, double tx, double ty, double tz) {
        double bx = isoX(tx, 0, tz), by = isoY(tx, 0, tz);
        double top = isoY(tx, 4, tz);

        // Trunk
        gc.setStroke(Color.web("#3d2b1a"));
        gc.setLineWidth(3);
        gc.strokeLine(bx, by, bx, by - 25);

        // Foliage layers (dark cones)
        Color[] greens = {Color.web("#142814"), Color.web("#1a3a1a"), Color.web("#204020")};
        for (int i = 0; i < 3; i++) {
            double cy = by - 20 - i * 14;
            double r = 16 - i * 3;
            gc.setFill(greens[i]);
            double[] px = {bx - r, bx, bx + r};
            double[] py = {cy + 10, cy - 10, cy + 10};
            gc.fillPolygon(px, py, 3);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  SIMULATION CONTROL
    // ══════════════════════════════════════════════════════════

    private void startSim() {
        if (running) return;
        int cap  = (int)slCap.getValue();
        int pd   = (int)slProdR.getValue();
        int cd   = (int)slConsR.getValue();
        int pt   = (int)slProcT.getValue();

        parkingLot = new ParkingLot(cap);
        metrics    = new SimulationMetrics();
        for (int i = 0; i < MAX_SLOTS; i++) { slotScale[i]=0; slotTarget[i]=0; slotColor[i]=0; slotBounce[i]=0; }

        producers.clear(); consumers.clear(); prodThreads.clear(); consThreads.clear();
        for (int i = 1; i <= DEF_PROD; i++) {
            CarProducer p = new CarProducer(i, parkingLot, metrics, pd);
            producers.add(p);
            Thread t = new Thread(p, "Producer-" + i); t.setDaemon(true); prodThreads.add(t);
        }
        for (int i = 1; i <= DEF_CONS; i++) {
            ParkingGuide c = new ParkingGuide(i, parkingLot, metrics, cd, pt);
            consumers.add(c);
            Thread t = new Thread(c, "Guide-" + i); t.setDaemon(true); consThreads.add(t);
        }
        prodThreads.forEach(Thread::start);
        consThreads.forEach(Thread::start);
        running = true;
        btnStart.setDisable(true); btnStop.setDisable(false);
        log("STARTED " + DEF_PROD + "P/" + DEF_CONS + "C cap=" + cap);
    }

    private void stopSim() {
        if (!running) return;
        producers.forEach(CarProducer::stop);
        consumers.forEach(ParkingGuide::stop);
        prodThreads.forEach(Thread::interrupt);
        consThreads.forEach(Thread::interrupt);
        running = false;
        btnStart.setDisable(false); btnStop.setDisable(true);
        log("STOPPED");
    }

    private void resetSim() {
        stopSim();
        producers.clear(); consumers.clear(); prodThreads.clear(); consThreads.clear();
        parkingLot=null; metrics=null;
        for (int i=0;i<MAX_SLOTS;i++){slotScale[i]=0;slotTarget[i]=0;slotBounce[i]=0;}
        lblOcc.setText("Occupancy: 0%"); lblTp.setText("Throughput: 0.00 /s");
        lblWt.setText("Avg Wait: 0 ms"); lblProd.setText("Produced: 0");
        lblCons.setText("Consumed: 0"); lblElapsed.setText("Elapsed: 0.0 s");
        threadBox.getChildren().clear(); logArea.clear();
        drawFrame(canvas.getGraphicsContext2D(), (int)slCap.getValue());
        log("RESET");
    }

    // ── UI timer ──
    private void startTimer() {
        new AnimationTimer() {
            private long last = 0;
            @Override public void handle(long now) {
                if (now - last < 33_000_000L) return; // ~30 fps
                last = now;
                int cap = (int) slCap.getValue();

                if (running && parkingLot != null && metrics != null) {
                    int occ = parkingLot.getOccupiedCount();
                    double pct = parkingLot.getOccupancyPercent();
                    lblOcc.setText(String.format("Occupancy: %.0f%%", pct));
                    lblOcc.setTextFill(pct>=90?Color.web("#FF4444"):pct>=60?Color.web("#FFAA33"):Color.web("#44BB88"));
                    lblTp.setText(String.format("Throughput: %.2f /s", metrics.getThroughput()));
                    lblWt.setText(String.format("Avg Wait: %.0f ms", metrics.getAverageWaitTimeMs()));
                    lblProd.setText("Produced: " + metrics.getTotalProduced());
                    lblCons.setText("Consumed: " + metrics.getTotalConsumed());
                    lblElapsed.setText(String.format("Elapsed: %.1f s", metrics.getElapsedSeconds()));
                    syncSlots(occ, cap);
                    updateThreads();
                }

                // Animate
                for (int i = 0; i < MAX_SLOTS; i++) {
                    double tgt = slotTarget[i] == 1 ? 1.0 : 0.0;
                    double diff = tgt - slotScale[i];
                    if (Math.abs(diff) > 0.01) {
                        slotScale[i] += diff * 0.12;
                        slotBounce[i] = Math.abs(diff) * 2; // bounce effect
                    } else {
                        slotScale[i] = tgt;
                        slotBounce[i] *= 0.85;
                    }
                }

                drawFrame(canvas.getGraphicsContext2D(), cap);
            }
        }.start();
    }

    private void syncSlots(int occ, int cap) {
        int rendered = 0;
        for (int i = 0; i < cap && i < MAX_SLOTS; i++) if (slotTarget[i]==1) rendered++;
        while (rendered < occ && rendered < cap) {
            for (int i = 0; i < cap && i < MAX_SLOTS; i++) {
                if (slotTarget[i]==0) {
                    slotTarget[i]=1; slotColor[i]=(metrics.getTotalProduced()+i)%CAR_COLS.length;
                    rendered++; break;
                }
            }
            if (rendered>=occ) break;
        }
        while (rendered > occ) {
            for (int i = Math.min(cap,MAX_SLOTS)-1; i>=0; i--) {
                if (slotTarget[i]==1) { slotTarget[i]=0; rendered--; break; }
            }
            if (rendered<=occ) break;
        }
        for (int i = cap; i < MAX_SLOTS; i++) slotTarget[i]=0;
    }

    private void updateThreads() {
        threadBox.getChildren().clear();
        for (CarProducer p : producers) threadBox.getChildren().add(tRow("Producer-"+p.getProducerId(), p.getStatus().name()));
        for (ParkingGuide c : consumers) threadBox.getChildren().add(tRow("Guide-"+c.getGuideId(), c.getStatus().name()));
    }

    // ══════════════════════════════════════════════════════════
    //  RIGHT PANEL
    // ══════════════════════════════════════════════════════════

    private VBox buildPanel() {
        Label title = mkLbl("Car Park Management Sim", 15, true, "#44AAFF");
        Label sub = mkLbl("Producer-Consumer Simulation", 10, false, "#556688");

        lblOcc = mLbl("Occupancy: 0%"); lblTp = mLbl("Throughput: 0.00 /s");
        lblWt = mLbl("Avg Wait: 0 ms"); lblProd = mLbl("Produced: 0");
        lblCons = mLbl("Consumed: 0"); lblElapsed = mLbl("Elapsed: 0.0 s");

        threadBox = new VBox(3);
        ScrollPane ts = new ScrollPane(threadBox); ts.setPrefHeight(100); ts.setFitToWidth(true);
        ts.setStyle("-fx-background:transparent;-fx-background-color:transparent;");

        slCap = sl(1,18,DEF_CAP); slProdR = sl(200,5000,DEF_PROD_MS);
        slConsR = sl(200,5000,DEF_CONS_MS); slProcT = sl(100,5000,DEF_PROC_MS);
        cvCap = vLbl(DEF_CAP+""); cvProd = vLbl(DEF_PROD_MS+"ms");
        cvCons = vLbl(DEF_CONS_MS+"ms"); cvProc = vLbl(DEF_PROC_MS+"ms");

        slCap.valueProperty().addListener((o,a,b)->{int v=b.intValue();cvCap.setText(v+"");if(parkingLot!=null)parkingLot.setCapacity(v);});
        slProdR.valueProperty().addListener((o,a,b)->{int v=b.intValue();cvProd.setText(v+"ms");producers.forEach(p->p.setProductionDelayMs(v));});
        slConsR.valueProperty().addListener((o,a,b)->{int v=b.intValue();cvCons.setText(v+"ms");consumers.forEach(c->c.setConsumptionDelayMs(v));});
        slProcT.valueProperty().addListener((o,a,b)->{int v=b.intValue();cvProc.setText(v+"ms");consumers.forEach(c->c.setProcessingTimeMs(v));});

        GridPane sg = new GridPane(); sg.setHgap(8); sg.setVgap(4);
        sg.addRow(0, sL("Capacity"), slCap, cvCap);
        sg.addRow(1, sL("Prod Rate"), slProdR, cvProd);
        sg.addRow(2, sL("Cons Rate"), slConsR, cvCons);
        sg.addRow(3, sL("Proc Time"), slProcT, cvProc);

        btnStart = btn("Start","#44BB88"); btnStop = btn("Stop","#FF5555"); btnReset = btn("Reset","#4488FF");
        btnStop.setDisable(true);
        btnStart.setOnAction(e->startSim()); btnStop.setOnAction(e->stopSim()); btnReset.setOnAction(e->resetSim());
        HBox bb = new HBox(8,btnStart,btnStop,btnReset); bb.setAlignment(Pos.CENTER);

        logArea = new TextArea(); logArea.setEditable(false); logArea.setPrefHeight(90);
        logArea.setFont(Font.font("Consolas",10));
        logArea.setStyle("-fx-control-inner-background:#0e0e22;-fx-text-fill:#6699bb;");

        VBox p = new VBox(6, title, sub, sep(),
                sec("DASHBOARD"), lblOcc, lblTp, lblWt, sep(), lblProd, lblCons, lblElapsed, sep(),
                sec("THREAD STATUS"), ts, sep(),
                sec("CONTROLS"), sg, sep(), bb, sep(),
                sec("EVENT LOG"), logArea);
        p.setPadding(new Insets(14)); p.setPrefWidth(300);
        p.setStyle("-fx-background-color:#0f1028;-fx-border-color:#1a2a44;-fx-border-width:0 0 0 1;");
        return p;
    }

    // Helpers
    private void log(String m){Platform.runLater(()->{logArea.appendText(String.format("[%tT] %s%n",System.currentTimeMillis(),m));});}
    private Label mkLbl(String t,double s,boolean b,String c){Label l=new Label(t);l.setFont(Font.font("Segoe UI",b?FontWeight.BOLD:FontWeight.NORMAL,s));l.setTextFill(Color.web(c));return l;}
    private Label mLbl(String t){Label l=new Label(t);l.setFont(Font.font("Consolas",12));l.setTextFill(Color.web("#AACCEE"));return l;}
    private Label sL(String t){Label l=new Label(t);l.setFont(Font.font("Segoe UI",11));l.setTextFill(Color.web("#8899AA"));l.setPrefWidth(75);return l;}
    private Label vLbl(String t){Label l=new Label(t);l.setFont(Font.font("Consolas",11));l.setTextFill(Color.web("#FFF"));l.setPrefWidth(50);return l;}
    private Label sec(String t){Label l=new Label(t);l.setFont(Font.font("Segoe UI",FontWeight.BOLD,11));l.setTextFill(Color.web("#44AAFF"));l.setPadding(new Insets(4,0,0,0));return l;}
    private Slider sl(double a,double b,double v){Slider s=new Slider(a,b,v);s.setPrefWidth(120);s.setBlockIncrement(1);return s;}
    private Button btn(String t,String c){Button b=new Button(t);b.setStyle("-fx-background-color:"+c+";-fx-text-fill:white;-fx-font-weight:bold;-fx-font-size:11;-fx-padding:7 18;-fx-background-radius:14;-fx-cursor:hand;");return b;}
    private Separator sep(){Separator s=new Separator();s.setStyle("-fx-background-color:#1a2a44;");return s;}
    private HBox tRow(String n,String st){
        String c=st.equals("ACTIVE")?"#44BB88":st.equals("WAITING")?"#FFD644":"#FF4444";
        Label d=new Label("\u25CF");d.setStyle("-fx-text-fill:"+c+";-fx-font-size:12;");
        Label l=new Label(n+" "+st);l.setFont(Font.font("Consolas",10));l.setTextFill(Color.web("#AACCEE"));
        HBox r=new HBox(5,d,l);r.setAlignment(Pos.CENTER_LEFT);return r;
    }

    /** Entry point. */
    public static void main(String[] args) { launch(args); }
}
