package ch.epfl.biop.demos;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvOverlay;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.demos.utils.BdvHelper;
import ch.epfl.biop.demos.utils.ShiftConverterSetupSliderCommand;
import net.imagej.ImageJ;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converter;
import net.imglib2.display.ColorTable;
import net.imglib2.position.FunctionRealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.DoubleType;
import org.scijava.Context;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaSwingUI;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.display.ConverterChanger;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

// Numerical integration errors sending pendulum into orbit

@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - Pendulum")
public class DemoPendulumCommand implements Command {

    @Parameter(visibility = ItemVisibility.MESSAGE)
    String description = "<html> <h1>Demo Pendulum</h1>\n" +
            "    <p>This demo illustrates the dynamics of a pendulum using different numerical integration schemes, visualized with ImgLib2 and BigDataViewer (BDV). The simulation showcases how different integration methods can affect the pendulum's motion due to numerical errors.</p>\n" +
            "\n" +
            "    <h2>Key Steps in the Demo:</h2>\n" +
            "\n" +
            "    <h3>1. Energy Landscape</h3>\n" +
            "    <p>A function is defined to compute the energy of the pendulum system at any point in its phase space, which is then visualized in BDV.</p>\n" +
            "\n" +
            "    <h3>2. Visualization Setup</h3>\n" +
            "    <p>A BDV handle is created and configured to display the energy landscape of the pendulum, allowing visualization of its dynamic behavior.</p>\n" +
            "\n" +
            "    <h3>3. Pendulum Simulation</h3>\n" +
            "    <p>Three pendulum simulations are set up using different numerical integration methods:</p>\n" +
            "    <ul>\n" +
            "        <li><strong>Explicit Euler</strong>: This method is straightforward but can lead to energy gain and instability in the simulation.</li>\n" +
            "        <li><strong>Implicit Euler</strong>: Known for its stability, this method tends to lose energy over time.</li>\n" +
            "        <li><strong>Runge-Kutta 4th Order</strong>: A more accurate method that balances stability and computational complexity to closely approximate the true dynamics.</li>\n" +
            "    </ul>\n" +
            "\n" +
            "    <h3>4. Display and Animation</h3>\n" +
            "    <p>The trajectories of the pendulum, calculated using each integration method, are animated and visualized over the energy landscape. This allows for a direct comparison of the different numerical methods' impacts on the pendulum's motion.</p>\n" +
            "\n" +
            "    <h3>5. GUI Setup and Interaction</h3>\n" +
            "    <p>The graphical user interface is configured to display the pendulum's motion and energy levels dynamically. An animation loop continuously updates the pendulum's position and repaints the viewer, illustrating the pendulum's trajectory and energy changes over time.</p>\n" +
            "\n" +
            "    <p>This demo provides insights into how different numerical methods can influence the behavior of dynamic systems like a pendulum, highlighting the trade-offs between accuracy and computational efficiency in simulations.</p>\n" +
            "    <br> Note that you can always explore the source code of the demo by clicking the <code>source</code> button.</br>" +
            "</html>";

    @Parameter // Its role is to make sure that the description is displayed
    boolean ok;

    @Parameter
    ConvertService cs;

    @Parameter
    Context context;

    @Parameter
    LogService logService;

    @Override
    public void run() {
        // d/dt(theta) = w
        // d/dt(w) = -sin(theta)
        // x axis = theta
        // y axis = w

        FunctionRealRandomAccessible<DoubleType> energy =
                new FunctionRealRandomAccessible<>(2, (position, value) -> {
                    double theta = position.getDoublePosition(0);
                    double w = position.getDoublePosition(1);
                    value.set(-Math.cos(theta)+1.0/2.0*w*w); // mind the .0 !
                }, DoubleType::new);

        BdvHandle bdvh = BdvHelper.display2D(energy, 255, 0, 0, -2, 10, "Energy", null);

        SourceAndConverter energySource = bdvh.getViewerPanel().state().getSources().get(0);

        // Change LUT of the energy source
        bdvh.getViewerPanel().state().removeSource(energySource);

        ColorTable table = BdvHelper.levels(20, 120);//ColorTables.SPECTRUM;//.ICE;

        Converter bdvLut = cs.convert(table, Converter.class);

        ConverterChanger cc = new ConverterChanger(energySource, bdvLut, bdvLut);
        cc.run();

        SourceAndConverter coloredEnergy = cc.get();

        SourceAndConverterServices.getBdvDisplayService().show(bdvh, coloredEnergy);

        // Puts a command into the bdv panel, which sets the energy level in the phase space
        bdvh.getCardPanel().addCard("Set energy level",
                ScijavaSwingUI.getPanel(context, ShiftConverterSetupSliderCommand.class,
                        "converter",
                        SourceAndConverterServices
                                .getSourceAndConverterService()
                                .getConverterSetup(coloredEnergy),
                        "width",2, "min", -1, "max", 1.5
                        ),
                true);

        FunctionRealRandomAccessible<RealPoint> pendulumPhaseSpace =
                new FunctionRealRandomAccessible<>(2, (position, value) -> {
                    double theta = position.getDoublePosition(0);
                    double w = position.getDoublePosition(1);
                    value.setPosition(w,0); // dtheta/dt = w
                    value.setPosition(-Math.sin(theta),1); // dw/dt = -sin(theta)
                }, () -> new RealPoint(3) );

        Pendulum pendulumEulerExplicit = new Pendulum(1.0, 0, "Explicit Euler");
        Pendulum pendulumEulerImplicit = new Pendulum(-1.0, 0, "Implicit Euler");
        Pendulum pendulumEulerRk4 = new Pendulum(0.0, Math.sqrt(2.0*Math.cos(1.0)), "Runge-Kutta 4");

        PointsOverlay overlay = new PointsOverlay();
        overlay.addPendulum(pendulumEulerExplicit);
        overlay.addPendulum(pendulumEulerImplicit);
        overlay.addPendulum(pendulumEulerRk4);

        BdvFunctions.showOverlay(overlay, "Points", BdvOptions.options().is2D().addTo(bdvh));

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            logService.error(e);
        }

        for (int i = 0;i<20000;i++) {
            pendulumEulerExplicit.explicitEulerStep(0.06, pendulumPhaseSpace);
            pendulumEulerImplicit.implicitEulerStep(0.06, pendulumPhaseSpace);
            pendulumEulerRk4.rk4Step(0.06, pendulumPhaseSpace);
            try {
                Thread.sleep(15);
            } catch (InterruptedException e) {
                logService.error(e);
            }
            bdvh.getViewerPanel().repaint();
        }

    }

    public static void main(final String... args) {
        // create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        ij.command().run(DemoPendulumCommand.class, true);
    }

    public static class Pendulum extends RealPoint {

        final int nDimensions;
        final String name;

        public Pendulum(double theta, double w, String name) {
            super(2);
            this.name = name;
            nDimensions = 2;
            setPosition(theta,0);
            setPosition(w,1);
        }

        public String toString() {
            return name;
        }

        // Explicit Euler integration scheme
        // Gaining energy - unstable
        public void explicitEulerStep(double dt, RealRandomAccessible<RealPoint> gradient) {
            RealPoint dir = gradient.getAt(Pendulum.this);
            for (int d = 0; d<nDimensions; d++) {
                Pendulum.this.setPosition(getDoublePosition(d)+dir.getDoublePosition(d)*dt, d);
            }
        }

        // Implicit Euler integration scheme
        // Losing energy : stable
        public void implicitEulerStep(double dt, RealRandomAccessible<RealPoint> gradient) {
            RealPoint gradientPosition = new RealPoint(this);
            RealPoint currentGradient = gradient.getAt(gradientPosition);
            for (int i=0;i<3;i++) {
                for (int d = 0; d < nDimensions; d++) {
                    gradientPosition.setPosition(getDoublePosition(d) + currentGradient.getDoublePosition(d) * dt, d);
                }
                currentGradient = gradient.getAt(gradientPosition);
            }
            for (int d = 0; d < Pendulum.this.numDimensions(); d++) {
                this.setPosition(getDoublePosition(d) + currentGradient.getDoublePosition(d) * dt, d);
            }
        }

        // Runge Kutta
        public void rk4Step(double dt, RealRandomAccessible<RealPoint> gradient) {

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
        }

    }

    public static class PointsOverlay extends BdvOverlay {

        public PointsOverlay() {

        }

        final List<Pendulum> pendulums = new ArrayList<>();

        final Color c = new Color(135, 255,60, 201);

        final int radius = 10;

        public void addPendulum(Pendulum pendulum) {
            pendulums.add(pendulum);
        }

        final Font defaultFont = new Font("TimesRoman", Font.BOLD, 18);

        @Override
        protected void draw(Graphics2D g) {
            g.setColor(c);
            g.setFont(defaultFont);
            AffineTransform3D t = new AffineTransform3D();
            this.getCurrentTransform3D(t);
            double[] lPos = new double[3];
            double[] gPos = new double[3];
            for (Pendulum pendulum : pendulums) {
                pendulum.localize( lPos );
                t.apply( lPos, gPos );
                final int x = ( int ) ( gPos[ 0 ] - radius );
                final int y = ( int ) ( gPos[ 1 ] - radius );
                g.fillOval( x, y, 2*radius, 2*radius );
                g.drawString(pendulum.toString(), (int) (gPos[ 0 ]), (int) (gPos[ 1 ]) - 20);
            }
        }
    }
}
