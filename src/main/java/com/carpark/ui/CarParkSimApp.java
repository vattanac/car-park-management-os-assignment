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
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Car Park Management Simulation with animated car driving.
 * <p>
 * Cars drive in from the road, queue when the lot is full,
 * park in an available slot, and drive out when consumed.
 * All rendered in an isometric nighttime style.
 * </p>
 *
 * @author vattanac
 */
public class CarParkSimApp extends Application {

    // ── Defaults ────────────────────────────────────────────
    private static final int DEF_CAP = 10, DEF_PROD = 3, DEF_CONS = 2;
    private static final int DEF_PROD_MS = 1500, DEF_CONS_MS = 800, DEF_PROC_MS = 1000;

    // ── Isometric ───────────────────────────────────────────
    private static final double TILE = 38;
    private double CX, CY;
    private double isoX(double x, double y, double z) { return CX + (x - z) * TILE * 0.87; }
    private double isoY(double x, double y, double z) { return CY + (x + z) * TILE * 0.5 - y * TILE; }

    // ── Car colours ─────────────────────────────────────────
    private static final Color[] CAR_C = {
        Color.web("#2266DD"), Color.web("#DD2244"), Color.web("#22BB55"),
        Color.web("#DD9911"), Color.web("#7733CC"), Color.web("#22BBBB"),
        Color.web("#CC2288"), Color.web("#44CC88"),
    };
    private static final Color[] CAR_D = {
        Color.web("#1144AA"), Color.web("#AA1133"), Color.web("#118833"),
        Color.web("#AA7700"), Color.web("#5522AA"), Color.web("#118888"),
        Color.web("#991166"), Color.web("#228855"),
    };

    // ── Canvas ──────────────────────────────────────────────
    private Canvas canvas;
    private static final double CW = 760, CH = 750;

    // ── Simulation ──────────────────────────────────────────
    private ParkingLot parkingLot;
    private SimulationMetrics metrics;
    private final List<CarProducer> producers  = new ArrayList<>();
    private final List<ParkingGuide> consumers = new ArrayList<>();
    private final List<Thread> prodThreads     = new ArrayList<>();
    private final List<Thread> consThreads     = new ArrayList<>();
    private boolean running = false;

    // ══════════════════════════════════════════════════════════
    //  ANIMATED CAR SYSTEM
    // ══════════════════════════════════════════════════════════

    /** States a visual car goes through. */
    enum CarState { QUEUING, DRIVING_IN, PARKED, DRIVING_OUT, GONE }

    /** An animated car with position and state. */
    static class AnimCar {
        int id;
        int colorIdx;
        CarState state;
        int slotIdx = -1; // assigned parking slot

        // Current world position
        double wx, wy, wz;
        // Target world position
        double tx, ty, tz;
        // Queue position on road
        double queueZ;

        double progress = 0; // 0..1 for animation lerp
        double speed = 0.035; // how fast it moves per frame

        AnimCar(int id, int colorIdx) {
            this.id = id;
            this.colorIdx = colorIdx;
            this.state = CarState.QUEUING;
        }
    }

    /** All active animated cars. */
    private final List<AnimCar> animCars = new ArrayList<>();

    /** Thread-safe queue of events from producer/consumer threads. */
    private final ConcurrentLinkedQueue<int[]> parkEvents   = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<int[]> removeEvents = new ConcurrentLinkedQueue<>();

    /** Slot occupancy: -1 = empty, else = animCar id. */
    private final int[] slotOwner = new int[18];

    /** Global car ID counter for anim cars. */
    private int nextAnimId = 0;

    // ── Slot world positions (computed on start) ────────────
    private double[][] slotPos; // [slotIdx][x, z]
    private int slotCols, slotRows;

    /** Road entry point (bottom of road in world space). */
    private static final double ROAD_X = 0, ROAD_Z_START = 13, ROAD_Z_LOT = 7;

    // ── Previous buffer size for detecting changes ──────────
    private int prevOccupied = 0;

    // ── UI ───────────────────────────────────────────────────
    private Label lblOcc, lblTp, lblWt, lblProd, lblCons, lblElapsed;
    private VBox threadBox;
    private Slider slCap, slProdR, slConsR, slProcT;
    private Button btnStart, btnStop, btnReset;
    private TextArea logArea;
    private long frameCount = 0;

    // ══════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ══════════════════════════════════════════════════════════

    @Override
    public void start(Stage stage) {
        stage.setTitle("Car Park Management Sim");
        CX = CW / 2; CY = CH * 0.36;

        for (int i = 0; i < slotOwner.length; i++) slotOwner[i] = -1;
        computeSlotPositions(DEF_CAP);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color:#080818;");
        canvas = new Canvas(CW, CH);
        StackPane cp = new StackPane(canvas);
        cp.setStyle("-fx-background-color:#080818;");
        root.setCenter(cp);
        root.setRight(buildPanel());

        Scene scene = new Scene(root, 1100, 750);
        stage.setScene(scene);
        stage.setMinWidth(950); stage.setMinHeight(650);
        stage.setOnCloseRequest(e -> stopSim());
        stage.show();

        startTimer();
    }

    @Override public void stop() { stopSim(); }

    /** Compute world positions for each parking slot based on capacity. */
    private void computeSlotPositions(int cap) {
        slotCols = (cap <= 5) ? cap : (cap <= 10) ? 5 : 6;
        slotRows = (int) Math.ceil(cap / (double) slotCols);
        double spotW = 2.2, spotD = 3.2;
        double totalW = slotCols * spotW;
        double totalD = slotRows * spotD;
        double startX = -totalW / 2, startZ = -totalD / 2 - 0.5;

        slotPos = new double[cap][2];
        for (int i = 0; i < cap; i++) {
            int c = i % slotCols, r = i / slotCols;
            slotPos[i][0] = startX + c * spotW + spotW / 2;
            slotPos[i][1] = startZ + r * spotD + spotD / 2;
        }
    }

    // ══════════════════════════════════════════════════════════
    //  ANIMATION ENGINE
    // ══════════════════════════════════════════════════════════

    /** Process events and advance all car animations. */
    private void tickAnimations(int cap) {
        // ── Handle new park events ──
        int currentOcc = (parkingLot != null) ? parkingLot.getOccupiedCount() : 0;

        // Detect new cars to add (occupancy went up)
        while (currentOcc > countParkedOrDrivingIn()) {
            // Find an empty slot
            int slot = findEmptySlot(cap);
            if (slot < 0) break;

            AnimCar ac = new AnimCar(nextAnimId++, nextAnimId % CAR_C.length);
            ac.state = CarState.QUEUING;
            ac.slotIdx = slot;
            slotOwner[slot] = ac.id;

            // Start on road
            int qPos = countQueuing();
            ac.wx = ROAD_X;
            ac.wz = ROAD_Z_START + qPos * 3.5;
            ac.wy = 0;
            ac.queueZ = ac.wz;

            animCars.add(ac);
            // Immediately start driving in
            ac.state = CarState.DRIVING_IN;
            ac.tx = slotPos[slot][0];
            ac.tz = slotPos[slot][1];
            ac.ty = 0;
            ac.progress = 0;
        }

        // Detect cars to remove (occupancy went down)
        while (currentOcc < countParked()) {
            // Find a parked car to remove (take the last one)
            AnimCar toRemove = null;
            for (int i = animCars.size() - 1; i >= 0; i--) {
                if (animCars.get(i).state == CarState.PARKED) {
                    toRemove = animCars.get(i);
                    break;
                }
            }
            if (toRemove == null) break;
            toRemove.state = CarState.DRIVING_OUT;
            toRemove.tx = ROAD_X;
            toRemove.tz = ROAD_Z_START + 2;
            toRemove.ty = 0;
            toRemove.progress = 0;
            if (toRemove.slotIdx >= 0 && toRemove.slotIdx < slotOwner.length) {
                slotOwner[toRemove.slotIdx] = -1;
            }
        }

        // ── Update queuing cars (line up on road) ──
        int qIdx = 0;
        for (AnimCar ac : animCars) {
            if (ac.state == CarState.QUEUING) {
                double targetZ = ROAD_Z_LOT + 1.5 + qIdx * 3.5;
                ac.wz += (targetZ - ac.wz) * 0.08;
                ac.wx += (ROAD_X - ac.wx) * 0.08;
                qIdx++;
            }
        }

        // ── Advance driving-in cars ──
        for (AnimCar ac : animCars) {
            if (ac.state == CarState.DRIVING_IN) {
                ac.progress += ac.speed;
                if (ac.progress >= 1.0) {
                    ac.progress = 1.0;
                    ac.wx = ac.tx; ac.wz = ac.tz; ac.wy = ac.ty;
                    ac.state = CarState.PARKED;
                } else {
                    // Two-phase: first drive to lot entrance, then to slot
                    double p = ac.progress;
                    if (p < 0.4) {
                        // Phase 1: road → lot entrance
                        double t = p / 0.4;
                        double ease = t * t * (3 - 2 * t); // smoothstep
                        ac.wx = lerp(ROAD_X, ROAD_X, ease);
                        ac.wz = lerp(ac.queueZ, ROAD_Z_LOT - 1, ease);
                        ac.wy = 0;
                    } else {
                        // Phase 2: lot entrance → slot
                        double t = (p - 0.4) / 0.6;
                        double ease = t * t * (3 - 2 * t);
                        ac.wx = lerp(ROAD_X, ac.tx, ease);
                        ac.wz = lerp(ROAD_Z_LOT - 1, ac.tz, ease);
                        ac.wy = Math.sin(t * Math.PI) * 0.1; // slight bounce
                    }
                }
            }
        }

        // ── Advance driving-out cars ──
        for (AnimCar ac : animCars) {
            if (ac.state == CarState.DRIVING_OUT) {
                ac.progress += ac.speed;
                if (ac.progress >= 1.0) {
                    ac.state = CarState.GONE;
                } else {
                    double startX = ac.wx, startZ = ac.wz;
                    if (ac.progress < 0.01) {
                        // Save starting position on first frame
                        ac.queueZ = ac.wz; // reuse field to store start z
                    }
                    double p = ac.progress;
                    double ease = p * p * (3 - 2 * p);

                    if (p < 0.5) {
                        // Phase 1: slot → lot entrance
                        double t = p / 0.5;
                        double e = t * t * (3 - 2 * t);
                        ac.wx = lerp(ac.wx, ROAD_X, e * 0.5);
                        ac.wz = lerp(ac.wz, ROAD_Z_LOT, e * 0.3);
                    } else {
                        // Phase 2: lot entrance → off screen
                        ac.wz += 0.25;
                        ac.wx += (ROAD_X - ac.wx) * 0.1;
                    }
                }
            }
        }

        // ── Remove GONE cars ──
        animCars.removeIf(ac -> ac.state == CarState.GONE);
    }

    private int countParkedOrDrivingIn() {
        int n = 0;
        for (AnimCar ac : animCars)
            if (ac.state == CarState.PARKED || ac.state == CarState.DRIVING_IN) n++;
        return n;
    }

    private int countParked() {
        int n = 0;
        for (AnimCar ac : animCars) if (ac.state == CarState.PARKED) n++;
        return n;
    }

    private int countQueuing() {
        int n = 0;
        for (AnimCar ac : animCars) if (ac.state == CarState.QUEUING) n++;
        return n;
    }

    private int findEmptySlot(int cap) {
        for (int i = 0; i < cap && i < slotOwner.length; i++)
            if (slotOwner[i] < 0) return i;
        return -1;
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    // ══════════════════════════════════════════════════════════
    //  SCENE RENDERER
    // ══════════════════════════════════════════════════════════

    private void drawFrame(GraphicsContext gc, int cap) {
        frameCount++;
        double w = CW, h = CH, time = frameCount * 0.02;

        // ── Sky ──
        gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#030510")), new Stop(0.4, Color.web("#080818")),
            new Stop(1, Color.web("#0b0f22"))));
        gc.fillRect(0, 0, w, h);

        // ── Stars ──
        Random rng = new Random(123);
        for (int i = 0; i < 120; i++) {
            double sx = rng.nextDouble() * w, sy = rng.nextDouble() * h * 0.45;
            double twinkle = 0.3 + 0.7 * (0.5 + 0.5 * Math.sin(time * 2 + i * 0.7));
            gc.setFill(Color.web("#aabbee", twinkle * 0.6));
            gc.fillOval(sx, sy, 0.8 + rng.nextDouble(), 0.8 + rng.nextDouble());
        }

        // ── Moon ──
        gc.setFill(Color.web("#ddeeff", 0.04)); gc.fillOval(w - 140, 30, 80, 80);
        gc.setFill(Color.web("#ddeeff", 0.08)); gc.fillOval(w - 125, 45, 50, 50);
        gc.setFill(Color.web("#ddeeff", 0.85)); gc.fillOval(w - 112, 55, 22, 22);

        // ── Ground ──
        double gs = 12;
        double[] gx = { isoX(-gs,0,0), isoX(0,0,-gs), isoX(gs,0,0), isoX(0,0,gs+6) };
        double[] gy = { isoY(-gs,0,0), isoY(0,0,-gs), isoY(gs,0,0), isoY(0,0,gs+6) };
        gc.setFill(Color.web("#0c1a0c")); gc.fillPolygon(gx, gy, 4);

        // ── Road ──
        double rw = 1.8;
        double[] rx = { isoX(-rw,0.01,7), isoX(rw,0.01,7), isoX(rw,0.01,18), isoX(-rw,0.01,18) };
        double[] ry = { isoY(-rw,0.01,7), isoY(rw,0.01,7), isoY(rw,0.01,18), isoY(-rw,0.01,18) };
        gc.setFill(Color.web("#161620")); gc.fillPolygon(rx, ry, 4);

        // Road center dashes
        for (int i = 0; i < 6; i++) {
            double dz = 7.5 + i * 1.6;
            double[] dx = { isoX(-0.06,0.02,dz), isoX(0.06,0.02,dz), isoX(0.06,0.02,dz+0.8), isoX(-0.06,0.02,dz+0.8) };
            double[] dy = { isoY(-0.06,0.02,dz), isoY(0.06,0.02,dz), isoY(0.06,0.02,dz+0.8), isoY(-0.06,0.02,dz+0.8) };
            gc.setFill(Color.web("#ccaa00", 0.5)); gc.fillPolygon(dx, dy, 4);
        }
        // Road edge lines
        for (double side : new double[]{-rw + 0.1, rw - 0.1}) {
            gc.setStroke(Color.web("#ffffff", 0.2)); gc.setLineWidth(0.8);
            gc.strokeLine(isoX(side,0.02,7), isoY(side,0.02,7), isoX(side,0.02,18), isoY(side,0.02,18));
        }

        // ── Asphalt lot ──
        double ls = 7;
        double[] lx = { isoX(-ls,0.01,-ls), isoX(ls,0.01,-ls), isoX(ls,0.01,ls), isoX(-ls,0.01,ls) };
        double[] ly = { isoY(-ls,0.01,-ls), isoY(ls,0.01,-ls), isoY(ls,0.01,ls), isoY(-ls,0.01,ls) };
        gc.setFill(Color.web("#1a1a2a")); gc.fillPolygon(lx, ly, 4);
        gc.setStroke(Color.web("#333355", 0.5)); gc.setLineWidth(1.5); gc.strokePolygon(lx, ly, 4);

        // ── Building ──
        drawIsoBox(gc, 0,2.5,-7.5, 12,5,1.5, Color.web("#1e2838"), Color.web("#161e2c"), Color.web("#141a26"));
        drawIsoBox(gc, 0,5.1,-7.5, 12.4,0.2,1.8, Color.web("#141a26"), Color.web("#0f1520"), Color.web("#0d121c"));
        for (int r = 0; r < 2; r++) for (int c = 0; c < 7; c++) {
            boolean lit = Math.sin(c*3.5+r*2.1) > -0.2;
            Color wc = lit ? Color.web("#ffdd88", 0.7) : Color.web("#0a0f18", 0.8);
            drawIsoBox(gc, -5+c*1.7, 1.2+r*2.2, -6.7, 0.8,1.0,0.05, wc, wc, wc);
        }
        drawIsoBox(gc, 0,5.5,-6.8, 3,0.6,0.1, Color.web("#1144cc"), Color.web("#0d33aa"), Color.web("#0a2288"));

        // ── Street lamps ──
        drawLamp(gc,-7,0,-7,time); drawLamp(gc,7,0,-7,time);
        drawLamp(gc,-7,0,5,time);  drawLamp(gc,7,0,5,time);

        // ── Trees ──
        drawTree(gc,-8.5,0,-6); drawTree(gc,8.5,0,-6);
        drawTree(gc,-8.5,0,6);  drawTree(gc,8.5,0,6);

        // ── Parking lines ──
        double spotW = 2.2, spotD = 3.2;
        double totalW = slotCols * spotW, totalD = slotRows * spotD;
        double startX = -totalW / 2, startZ = -totalD / 2 - 0.5;
        for (int i = 0; i < cap; i++) {
            int c = i % slotCols; int rr = i / slotCols;
            double sx = startX + c * spotW, sz = startZ + rr * spotD;
            drawParkingLines(gc, sx, sz, spotW, spotD);
            // Empty slot label
            if (slotOwner[i] < 0) {
                double cx = isoX(sx + spotW/2, 0, sz + spotD/2);
                double cy = isoY(sx + spotW/2, 0, sz + spotD/2);
                gc.setFill(Color.web("#334455", 0.35));
                gc.setFont(Font.font("Consolas", 8)); gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText("P" + (i+1), cx, cy+3);
            }
        }

        // ── Curbs ──
        drawIsoBox(gc, -2,0.1,7, 4,0.15,0.15, Color.web("#555555"), Color.web("#444444"), Color.web("#333333"));

        // ── Draw all animated cars (sorted by z for depth) ──
        animCars.sort((a, b) -> Double.compare(a.wz, b.wz));
        for (AnimCar ac : animCars) {
            if (ac.state != CarState.GONE) {
                double scale = 1.0;
                if (ac.state == CarState.DRIVING_IN && ac.progress < 0.2)
                    scale = 0.5 + ac.progress * 2.5; // grow as approaching
                if (ac.state == CarState.DRIVING_OUT && ac.progress > 0.7)
                    scale = 1.0 - (ac.progress - 0.7) * 3.3; // shrink as leaving
                scale = Math.max(0.05, Math.min(1.0, scale));

                // Headlight glow when driving
                boolean headlightsOn = (ac.state == CarState.DRIVING_IN || ac.state == CarState.DRIVING_OUT || ac.state == CarState.QUEUING);
                drawIsoCar(gc, ac.wx, ac.wy, ac.wz, scale, ac.colorIdx, headlightsOn);
            }
        }

        // ── Lot counter ──
        int visCount = countParked();
        gc.setFill(Color.web("#44aaff", 0.07));
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 28)); gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("C A R   P A R K", 30, h - 28);
        gc.setFill(Color.web("#aaccee", 0.5));
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 13)); gc.setTextAlign(TextAlignment.RIGHT);
        gc.fillText(visCount + " / " + cap, CW - 30, h - 28);

        // ── Queue count ──
        int queueCount = countQueuing();
        if (queueCount > 0) {
            gc.setFill(Color.web("#ffaa33", 0.7));
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, 11)); gc.setTextAlign(TextAlignment.CENTER);
            double qx = isoX(ROAD_X, 0, ROAD_Z_LOT + 3);
            double qy = isoY(ROAD_X, 1.5, ROAD_Z_LOT + 3);
            gc.fillText(queueCount + " waiting", qx, qy);
        }
    }

    // ── Isometric box ──
    private void drawIsoBox(GraphicsContext gc, double cx, double cy, double cz,
                            double bw, double bh, double bd, Color top, Color front, Color side) {
        double hw = bw/2, hd = bd/2;
        double[] tx = { isoX(cx-hw,cy+bh,cz-hd), isoX(cx+hw,cy+bh,cz-hd), isoX(cx+hw,cy+bh,cz+hd), isoX(cx-hw,cy+bh,cz+hd) };
        double[] ty = { isoY(cx-hw,cy+bh,cz-hd), isoY(cx+hw,cy+bh,cz-hd), isoY(cx+hw,cy+bh,cz+hd), isoY(cx-hw,cy+bh,cz+hd) };
        gc.setFill(top); gc.fillPolygon(tx, ty, 4);
        double[] fx = { isoX(cx-hw,cy,cz+hd), isoX(cx+hw,cy,cz+hd), isoX(cx+hw,cy+bh,cz+hd), isoX(cx-hw,cy+bh,cz+hd) };
        double[] fy = { isoY(cx-hw,cy,cz+hd), isoY(cx+hw,cy,cz+hd), isoY(cx+hw,cy+bh,cz+hd), isoY(cx-hw,cy+bh,cz+hd) };
        gc.setFill(front); gc.fillPolygon(fx, fy, 4);
        double[] sx = { isoX(cx+hw,cy,cz-hd), isoX(cx+hw,cy,cz+hd), isoX(cx+hw,cy+bh,cz+hd), isoX(cx+hw,cy+bh,cz-hd) };
        double[] sy = { isoY(cx+hw,cy,cz-hd), isoY(cx+hw,cy,cz+hd), isoY(cx+hw,cy+bh,cz+hd), isoY(cx+hw,cy+bh,cz-hd) };
        gc.setFill(side); gc.fillPolygon(sx, sy, 4);
    }

    // ── Isometric car ──
    private void drawIsoCar(GraphicsContext gc, double wx, double wy, double wz,
                            double scale, int ci, boolean headlights) {
        double bw = 1.5 * scale, bh = 0.45 * scale, bd = 2.6 * scale;
        Color body = CAR_C[ci % CAR_C.length], dark = CAR_D[ci % CAR_D.length];

        // Ground shadow
        gc.setFill(Color.web("#000000", 0.2 * scale));
        double shx = isoX(wx, 0, wz), shy = isoY(wx, 0, wz);
        gc.fillOval(shx - 22*scale, shy - 7*scale, 44*scale, 14*scale);

        // Body
        drawIsoBox(gc, wx, wy+0.12, wz, bw, bh, bd, body, dark, dark.darker());
        // Cabin
        drawIsoBox(gc, wx, wy+0.12+bh, wz-bd*0.04, bw*0.78, bh*0.65, bd*0.48,
                   Color.web("#88ccff", 0.3), Color.web("#6699cc", 0.25), Color.web("#5588bb", 0.2));
        // Roof
        drawIsoBox(gc, wx, wy+0.12+bh+bh*0.65, wz-bd*0.04, bw*0.8, bh*0.1, bd*0.46, body, dark, dark.darker());

        // Headlights
        double hlY = wy + 0.32;
        if (headlights) {
            gc.setFill(Color.web("#ffffcc", 0.9));
            gc.fillOval(isoX(wx-bw*0.3, hlY, wz+bd/2)-2.5, isoY(wx-bw*0.3, hlY, wz+bd/2)-2, 5*scale, 4*scale);
            gc.fillOval(isoX(wx+bw*0.3, hlY, wz+bd/2)-2.5, isoY(wx+bw*0.3, hlY, wz+bd/2)-2, 5*scale, 4*scale);
            // Light beam
            gc.setFill(Color.web("#ffffaa", 0.05 * scale));
            double bx = isoX(wx, hlY, wz+bd/2+2), by = isoY(wx, 0, wz+bd/2+2);
            gc.fillOval(bx-20*scale, by-6*scale, 40*scale, 12*scale);
        }

        // Tail lights
        gc.setFill(Color.web("#ff2200", 0.75));
        gc.fillOval(isoX(wx-bw*0.3, hlY, wz-bd/2)-1.5, isoY(wx-bw*0.3, hlY, wz-bd/2)-1.5, 4*scale, 3*scale);
        gc.fillOval(isoX(wx+bw*0.3, hlY, wz-bd/2)-1.5, isoY(wx+bw*0.3, hlY, wz-bd/2)-1.5, 4*scale, 3*scale);

        // Wheels
        gc.setFill(Color.web("#0a0a0a"));
        double[][] wp = {{wx-bw/2, wy+0.06, wz+bd*0.28}, {wx+bw/2, wy+0.06, wz+bd*0.28},
                         {wx-bw/2, wy+0.06, wz-bd*0.28}, {wx+bw/2, wy+0.06, wz-bd*0.28}};
        for (double[] p : wp) gc.fillOval(isoX(p[0],p[1],p[2])-3*scale, isoY(p[0],p[1],p[2])-2*scale, 6*scale, 4*scale);
    }

    // ── Parking lines ──
    private void drawParkingLines(GraphicsContext gc, double sx, double sz, double sw, double sd) {
        gc.setStroke(Color.web("#ffffff", 0.15)); gc.setLineWidth(0.8);
        gc.strokeLine(isoX(sx,0.02,sz),isoY(sx,0.02,sz), isoX(sx,0.02,sz+sd),isoY(sx,0.02,sz+sd));
        gc.strokeLine(isoX(sx+sw,0.02,sz),isoY(sx+sw,0.02,sz), isoX(sx+sw,0.02,sz+sd),isoY(sx+sw,0.02,sz+sd));
        gc.strokeLine(isoX(sx,0.02,sz),isoY(sx,0.02,sz), isoX(sx+sw,0.02,sz),isoY(sx+sw,0.02,sz));
    }

    // ── Lamp ──
    private void drawLamp(GraphicsContext gc, double lx, double ly, double lz, double time) {
        double bx=isoX(lx,0,lz), by=isoY(lx,0,lz), txx=isoX(lx,3.5,lz), txy=isoY(lx,3.5,lz);
        gc.setStroke(Color.web("#555555")); gc.setLineWidth(1.5); gc.strokeLine(bx,by,txx,txy);
        double f = 0.8 + 0.2 * Math.sin(time*3+lx*2);
        gc.setFill(Color.web("#ffaa44", 0.05*f)); gc.fillOval(bx-30,by-10,60,20);
        gc.setFill(Color.web("#ffdd88", 0.85*f)); gc.fillOval(txx-3,txy-3,6,6);
        gc.setFill(Color.web("#ffaa44", 0.1*f)); gc.fillOval(txx-10,txy-6,20,12);
    }

    // ── Tree ──
    private void drawTree(GraphicsContext gc, double tx, double ty, double tz) {
        double bx=isoX(tx,0,tz), by=isoY(tx,0,tz);
        gc.setStroke(Color.web("#3d2b1a")); gc.setLineWidth(3); gc.strokeLine(bx,by,bx,by-22);
        Color[] gr = {Color.web("#142814"), Color.web("#1a3a1a"), Color.web("#204020")};
        for (int i = 0; i < 3; i++) {
            double cy = by-18-i*12; double r = 14-i*3;
            gc.setFill(gr[i]); gc.fillPolygon(new double[]{bx-r,bx,bx+r}, new double[]{cy+8,cy-8,cy+8}, 3);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  SIMULATION CONTROL
    // ══════════════════════════════════════════════════════════

    private void startSim() {
        if (running) return;
        int cap=(int)slCap.getValue(); computeSlotPositions(cap);
        parkingLot = new ParkingLot(cap); metrics = new SimulationMetrics();
        for (int i = 0; i < slotOwner.length; i++) slotOwner[i]=-1;
        animCars.clear(); nextAnimId = 0;

        producers.clear(); consumers.clear(); prodThreads.clear(); consThreads.clear();
        for (int i=1;i<=DEF_PROD;i++){
            CarProducer p=new CarProducer(i,parkingLot,metrics,(int)slProdR.getValue());
            producers.add(p); Thread t=new Thread(p,"Producer-"+i); t.setDaemon(true); prodThreads.add(t);
        }
        for (int i=1;i<=DEF_CONS;i++){
            ParkingGuide c=new ParkingGuide(i,parkingLot,metrics,(int)slConsR.getValue(),(int)slProcT.getValue());
            consumers.add(c); Thread t=new Thread(c,"Guide-"+i); t.setDaemon(true); consThreads.add(t);
        }
        prodThreads.forEach(Thread::start); consThreads.forEach(Thread::start);
        running=true; btnStart.setDisable(true); btnStop.setDisable(false);
        log("STARTED "+DEF_PROD+"P/"+DEF_CONS+"C cap="+cap);
    }

    private void stopSim() {
        if (!running) return;
        producers.forEach(CarProducer::stop); consumers.forEach(ParkingGuide::stop);
        prodThreads.forEach(Thread::interrupt); consThreads.forEach(Thread::interrupt);
        running=false; btnStart.setDisable(false); btnStop.setDisable(true); log("STOPPED");
    }

    private void resetSim() {
        stopSim();
        producers.clear(); consumers.clear(); prodThreads.clear(); consThreads.clear();
        parkingLot=null; metrics=null; animCars.clear();
        for (int i=0;i<slotOwner.length;i++) slotOwner[i]=-1;
        lblOcc.setText("Occupancy: 0%"); lblTp.setText("Throughput: 0.00 /s");
        lblWt.setText("Avg Wait: 0 ms"); lblProd.setText("Produced: 0");
        lblCons.setText("Consumed: 0"); lblElapsed.setText("Elapsed: 0.0 s");
        threadBox.getChildren().clear(); logArea.clear();
        computeSlotPositions((int)slCap.getValue());
        log("RESET");
    }

    // ── Timer ──
    private void startTimer() {
        new AnimationTimer() {
            private long last = 0;
            @Override public void handle(long now) {
                if (now-last < 33_000_000L) return; last=now;
                int cap = (int) slCap.getValue();
                if (running && parkingLot!=null && metrics!=null) {
                    double pct=parkingLot.getOccupancyPercent();
                    lblOcc.setText(String.format("Occupancy: %.0f%%", pct));
                    lblOcc.setTextFill(pct>=90?Color.web("#FF4444"):pct>=60?Color.web("#FFAA33"):Color.web("#44BB88"));
                    lblTp.setText(String.format("Throughput: %.2f /s", metrics.getThroughput()));
                    lblWt.setText(String.format("Avg Wait: %.0f ms", metrics.getAverageWaitTimeMs()));
                    lblProd.setText("Produced: "+metrics.getTotalProduced());
                    lblCons.setText("Consumed: "+metrics.getTotalConsumed());
                    lblElapsed.setText(String.format("Elapsed: %.1f s", metrics.getElapsedSeconds()));
                    tickAnimations(cap);
                    updateThreads();
                }
                drawFrame(canvas.getGraphicsContext2D(), cap);
            }
        }.start();
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
        Label title=mkL("Car Park Management Sim",15,true,"#44AAFF");
        Label sub=mkL("Producer-Consumer Simulation",10,false,"#556688");
        lblOcc=mL("Occupancy: 0%"); lblTp=mL("Throughput: 0.00 /s"); lblWt=mL("Avg Wait: 0 ms");
        lblProd=mL("Produced: 0"); lblCons=mL("Consumed: 0"); lblElapsed=mL("Elapsed: 0.0 s");
        threadBox=new VBox(3); ScrollPane ts=new ScrollPane(threadBox); ts.setPrefHeight(100);
        ts.setFitToWidth(true); ts.setStyle("-fx-background:transparent;-fx-background-color:transparent;");

        slCap=sl(1,18,DEF_CAP); slProdR=sl(200,5000,DEF_PROD_MS);
        slConsR=sl(200,5000,DEF_CONS_MS); slProcT=sl(100,5000,DEF_PROC_MS);

        // Editable text fields synced with sliders
        TextField tfCap  = numField(DEF_CAP+"", 1, 18, slCap, "");
        TextField tfProd = numField(DEF_PROD_MS+"", 200, 5000, slProdR, "ms");
        TextField tfCons = numField(DEF_CONS_MS+"", 200, 5000, slConsR, "ms");
        TextField tfProc = numField(DEF_PROC_MS+"", 100, 5000, slProcT, "ms");

        // Slider → TextField sync
        slCap.valueProperty().addListener((o,a,b)->{
            int v=b.intValue(); tfCap.setText(v+"");
            if(parkingLot!=null){parkingLot.setCapacity(v);computeSlotPositions(v);}
        });
        slProdR.valueProperty().addListener((o,a,b)->{
            int v=b.intValue(); tfProd.setText(v+"");
            producers.forEach(p->p.setProductionDelayMs(v));
        });
        slConsR.valueProperty().addListener((o,a,b)->{
            int v=b.intValue(); tfCons.setText(v+"");
            consumers.forEach(c->c.setConsumptionDelayMs(v));
        });
        slProcT.valueProperty().addListener((o,a,b)->{
            int v=b.intValue(); tfProc.setText(v+"");
            consumers.forEach(c->c.setProcessingTimeMs(v));
        });

        GridPane sg=new GridPane(); sg.setHgap(6); sg.setVgap(5);
        sg.addRow(0, sL("Capacity"), slCap, tfCap);
        sg.addRow(1, sL("Prod Rate"), slProdR, tfProd);
        sg.addRow(2, sL("Cons Rate"), slConsR, tfCons);
        sg.addRow(3, sL("Proc Time"), slProcT, tfProc);

        btnStart=btn("Start","#44BB88"); btnStop=btn("Stop","#FF5555"); btnReset=btn("Reset","#4488FF");
        btnStop.setDisable(true);
        btnStart.setOnAction(e->startSim()); btnStop.setOnAction(e->stopSim()); btnReset.setOnAction(e->resetSim());
        HBox bb=new HBox(8,btnStart,btnStop,btnReset); bb.setAlignment(Pos.CENTER);

        logArea=new TextArea(); logArea.setEditable(false); logArea.setPrefHeight(90);
        logArea.setFont(Font.font("Consolas",10));
        logArea.setStyle("-fx-control-inner-background:#0e0e22;-fx-text-fill:#6699bb;");

        VBox p=new VBox(6, title, sub, sep(),
            sec("DASHBOARD"), lblOcc, lblTp, lblWt, sep(), lblProd, lblCons, lblElapsed, sep(),
            sec("THREAD STATUS"), ts, sep(), sec("CONTROLS"), sg, sep(), bb, sep(),
            sec("EVENT LOG"), logArea);
        p.setPadding(new Insets(14)); p.setPrefWidth(300);
        p.setStyle("-fx-background-color:#0f1028;-fx-border-color:#1a2a44;-fx-border-width:0 0 0 1;");
        return p;
    }

    // ── Helpers ──
    private void log(String m){Platform.runLater(()->{logArea.appendText(String.format("[%tT] %s%n",System.currentTimeMillis(),m));});}
    private Label mkL(String t,double s,boolean b,String c){Label l=new Label(t);l.setFont(Font.font("Segoe UI",b?FontWeight.BOLD:FontWeight.NORMAL,s));l.setTextFill(Color.web(c));return l;}
    private Label mL(String t){Label l=new Label(t);l.setFont(Font.font("Consolas",12));l.setTextFill(Color.web("#AACCEE"));return l;}
    private Label sL(String t){Label l=new Label(t);l.setFont(Font.font("Segoe UI",11));l.setTextFill(Color.web("#8899AA"));l.setPrefWidth(75);return l;}

    /**
     * Creates an editable numeric TextField synced bidirectionally with a Slider.
     * Type a value and press Enter to update the slider; slider changes update the field.
     *
     * @param initial  initial display text
     * @param min      minimum allowed value
     * @param max      maximum allowed value
     * @param slider   the slider to sync with
     * @param suffix   display suffix (e.g. "ms") — only shown in placeholder
     * @return the styled TextField
     */
    private TextField numField(String initial, int min, int max, Slider slider, String suffix) {
        TextField tf = new TextField(initial);
        tf.setPrefWidth(58);
        tf.setFont(Font.font("Consolas", 11));
        tf.setStyle("-fx-background-color:#1a1a33;-fx-text-fill:#ffffff;-fx-border-color:#334466;"
                + "-fx-border-radius:4;-fx-background-radius:4;-fx-padding:3 6;");
        tf.setPromptText(min + "-" + max + suffix);

        // On Enter or focus lost: parse and apply
        Runnable apply = () -> {
            try {
                String text = tf.getText().replaceAll("[^0-9]", "");
                if (text.isEmpty()) return;
                int val = Integer.parseInt(text);
                val = Math.max(min, Math.min(max, val));
                slider.setValue(val);
                tf.setText(val + "");
            } catch (NumberFormatException ignored) {
                tf.setText((int) slider.getValue() + "");
            }
        };
        tf.setOnAction(e -> apply.run());
        tf.focusedProperty().addListener((o, wasFocused, isFocused) -> {
            if (!isFocused) apply.run();
        });

        return tf;
    }
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

    public static void main(String[] args) { launch(args); }
}
