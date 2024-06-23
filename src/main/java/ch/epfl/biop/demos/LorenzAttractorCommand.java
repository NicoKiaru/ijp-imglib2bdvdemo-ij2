package ch.epfl.biop.demos;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvOverlay;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.demos.utils.BdvHelper;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converter;
import net.imglib2.display.ColorTable;
import net.imglib2.position.FunctionRealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.DoubleType;
import org.scijava.command.Command;
import org.scijava.convert.ConvertService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.display.ConverterChanger;

import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

//dx/dt = sigma(y-x)
//dy/dt = x(rho-z)-y
//dz/dt = xy-beta.z

@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - Lorenz Attractor")
public class LorenzAttractorCommand implements Command {

    @Parameter
    double sigma = 10;

    @Parameter
    double rho = 28;

    @Parameter
    double beta = 8.0 / 3.0;

    @Parameter
    ConvertService cs;

    @Override
    public void run() {
        final FunctionRealRandomAccessible<RealPoint> lorenzGradient =
                new FunctionRealRandomAccessible<>(3, (position, value) -> {
                    double x = position.getDoublePosition(0);
                    double y = position.getDoublePosition(1);
                    double z = position.getDoublePosition(2);
                    value.setPosition(sigma * (y-x),0); // dx/dt = sigma(y-x)
                    value.setPosition(x*(rho-z)-y,1); // dy/dt = x(rho-z)-y
                    value.setPosition(x*y-beta*z,2); // dz/dt = xy-beta.z
                }, () -> new RealPoint(3) );

        FunctionRealRandomAccessible<DoubleType> lorenzSpeed
                = new FunctionRealRandomAccessible<>(3, (position, value) -> {
                    RealPoint gradient = new RealPoint(lorenzGradient.getAt(position));
            double gx = gradient.getDoublePosition(0);
            double gy = gradient.getDoublePosition(1);
            double gz = gradient.getDoublePosition(2);
            value.set(Math.sqrt(gx*gx+gy*gy+gz*gz));
        }, DoubleType::new);

        BdvHandle bdvh = BdvHelper.display3D(lorenzSpeed, 255, 0, 0, -2, 10, "Gradient Intensity", null);

        SourceAndConverter<DoubleType> energySource = (SourceAndConverter<DoubleType>) bdvh.getViewerPanel().state().getSources().get(0);

        // Change LUT of the energy source
        bdvh.getViewerPanel().state().removeSource(energySource);

        ColorTable table = BdvHelper.levels(10, 120);//ColorTables.SPECTRUM;//.ICE;

        Converter<DoubleType, ARGBType> bdvLut = cs.convert(table, Converter.class);

        ConverterChanger<DoubleType> cc = new ConverterChanger(energySource, bdvLut, bdvLut);
        cc.run();

        SourceAndConverter<DoubleType> coloredEnergy = cc.get();

        SourceAndConverterServices.getBdvDisplayService().show(bdvh, coloredEnergy);

        List<Particle> particles = new ArrayList<>();
        particles.add(new Particle(1,1,1,"Particle 0", new Color(166, 29, 180,255)));
        particles.add(new Particle(1.01,1,1,"Particle 1", new Color(28, 106, 21,255)));

        ParticleOverlay overlay = new ParticleOverlay();
        for (Particle particle : particles) {
            overlay.addParticle(particle);
        }

        BdvFunctions.showOverlay(overlay, "Points", BdvOptions.options().addTo(bdvh));

        AtomicBoolean viewerClosed = new AtomicBoolean(false);

        final Thread animate = new Thread(() -> {
            while (!viewerClosed.get()) {
                for (Particle particle : particles)  particle.step(0.01, lorenzGradient);
                try { Thread.sleep(15); } catch (InterruptedException e) { System.err.println("Animator interrupted: "+e.getMessage()); }
                bdvh.getViewerPanel().repaint();
            }
            System.out.println("Lorenz Animator stopped");
        });

        animate.start();

        bdvh.getViewerPanel().addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent event) {}
            @Override
            public void ancestorMoved(AncestorEvent event) {}
            @Override
            public void ancestorRemoved(AncestorEvent event) {
                viewerClosed.set(true);
            }
        });

    }

    public static class Trail {
        final int length;
        Color color;
        int r,g,b,a;

        public Trail(int length, Color color) {
            this.length = length;
            this.color = color;
            r = color.getRed();
            g = color.getGreen();
            b = color.getBlue();
            a = color.getAlpha();
        }

        public Color getColor(int index) {
            return new Color(r,g,b,255-(a * index / length));
        }

        LinkedList<RealPoint> points = new LinkedList<>();

        synchronized public void addPoint(RealPoint point) {
            points.addFirst(new RealPoint(point));
            if (points.size()>length) {
                points.removeLast();
            }
        }

        synchronized public LinkedList<RealPoint> getPoints() {
            return new LinkedList<>(points);
        }
    }

    public static class Particle extends RealPoint {

        final int nDimensions;
        final String name;
        final Trail trail;
        int trailStep = 1;
        int step = 0;
        final Color color;

        public Particle(double x, double y, double z, String name, Color color) {
            super(3);
            this.name = name;
            nDimensions = 3;
            setPosition(x,0);
            setPosition(y,1);
            setPosition(z,2);
            this.color = color;
            trail = new Trail(900, color);
        }

        public Color getColor() {
            return color;
        }

        public String toString() {
            return name;
        }

        public Trail getTrail() {
            return trail;
        }

        // Runge Kutta 4
        public void step(double dt, RealRandomAccessible<RealPoint> gradient) {

            RealPoint p1 = new RealPoint(this);
            RealPoint k1 = new RealPoint(gradient.getAt(p1));

            RealPoint p2 = new RealPoint(nDimensions);
            for (int d = 0; d < nDimensions; d++) {
                p2.setPosition(getDoublePosition(d) + k1.getDoublePosition(d) * dt/2.0, d);
            }
            RealPoint k2 = new RealPoint(gradient.getAt(p2));

            RealPoint p3 = new RealPoint(nDimensions);
            for (int d = 0; d < nDimensions; d++) {
                p3.setPosition(getDoublePosition(d) + k2.getDoublePosition(d) * dt/2.0, d);
            }
            RealPoint k3 = new RealPoint(gradient.getAt(p3));

            RealPoint p4 = new RealPoint(nDimensions);
            for (int d = 0; d < nDimensions; d++) {
                p4.setPosition(getDoublePosition(d) + k3.getDoublePosition(d) * dt, d);
            }
            RealPoint k4 = new RealPoint(gradient.getAt(p4));

            for (int d = 0; d < nDimensions; d++) {
                this.setPosition(getDoublePosition(d) + (k1.getDoublePosition(d) + 2.0 * k2.getDoublePosition(d) + 2.0 * k3.getDoublePosition(d) + k4.getDoublePosition(d)) * dt/6.0, d);
            }
            step = step + 1;
            if ((step % trailStep) == 0) {
                step = 0;
                trail.addPoint(this);
            }
        }

    }

    public static class ParticleOverlay extends BdvOverlay {

        List<Particle> particles = new ArrayList<>();

        final int radius = 10;

        public synchronized void addParticle(Particle particle) {
            particles.add(particle);
        }

        Font defaultFont = new Font("TimesRoman", Font.BOLD, 18);

        final static float[] dash1 = {10.0f};

        final static BasicStroke dashed =
                new BasicStroke(2.0f,
                        BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_MITER,
                        10.0f, dash1, 0.0f);

        final static BasicStroke normal =
                new BasicStroke(4.0f);

        @Override
        protected void draw(Graphics2D g) {
            g.setFont(defaultFont);
            g.setStroke(dashed);
            AffineTransform3D t = new AffineTransform3D();
            this.getCurrentTransform3D(t);
            double[] lPos = new double[3];
            double[] gPos = new double[3];
            for (Particle particle : particles) {
                g.setColor(particle.getColor());
                particle.localize( lPos );
                t.apply( lPos, gPos );
                final int radius = this.radius;//(int)(Math.max(maxRadius-Math.abs(gPos[2]),minRadius));
                final int x = ( int ) ( gPos[ 0 ] - radius );
                final int y = ( int ) ( gPos[ 1 ] - radius );
                g.fillOval( x, y, 2*radius, 2*radius );
                int previousX = x+radius;
                int previousY = y+radius;
                double previousZ = gPos[2];
                List<RealPoint> trail = particle.getTrail().getPoints();
                int index = 0;
                if (gPos[2]>0) {
                    g.setStroke(normal);
                } else {
                    g.setStroke(dashed);
                }
                for (RealPoint rp : trail) {
                    g.setColor(particle.getTrail().getColor(index));
                    rp.localize( lPos );
                    t.apply( lPos, gPos );
                    final int xTrail = ( int ) ( gPos[ 0 ] );
                    final int yTrail = ( int ) ( gPos[ 1 ] );
                    if (gPos[2]*previousZ<=0) {
                        if (gPos[2]==previousZ) gPos[2]+=0.01; // Hmmm

                        double ratio = previousZ / (previousZ-gPos[2]);

                        g.drawLine(previousX, previousY,
                                (int)(previousX + ratio*(xTrail-previousX)),
                                (int)(previousY + ratio*(yTrail-previousY)));

                        // Need to switch
                        if (gPos[2]>0) {
                            g.setStroke(normal);
                        } else {
                            g.setStroke(dashed);
                        }
                        g.drawLine(
                                (int)(previousX + ratio*(xTrail-previousX)),
                                (int)(previousY + ratio*(yTrail-previousY)),xTrail, yTrail);
                    } else {
                        g.drawLine(previousX, previousY, xTrail, yTrail);
                    }
                    previousX = xTrail;
                    previousY = yTrail;
                    previousZ = gPos[2];
                    index++;
                }
            }
        }
    }

}
