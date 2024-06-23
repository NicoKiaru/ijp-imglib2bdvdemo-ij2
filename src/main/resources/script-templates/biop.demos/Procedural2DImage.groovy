

FunctionRealRandomAccessible<DoubleType> wave =
        new FunctionRealRandomAccessible<>(2, {
            position, pixel ->
                double px = position.getDoublePosition(0)
                double py = position.getDoublePosition(1)
                pixel.set(Math.cos(px)*Math.sin(py))
        } as BiConsumer, DoubleType::new)

def bdvStack = BdvFunctions.show(wave, Intervals.createMinMax(0,0,0,1,1,1), "Wave")
bdvStack.setDisplayRange(-1,1)
bdvStack.setColor(new ARGBType(ARGBType.rgba(255.0, 120.0, 0.0, 0.0)))

import net.imglib2.type.numeric.ARGBType

import java.util.function.BiConsumer
import bdv.util.BdvFunctions
import net.imglib2.position.FunctionRealRandomAccessible
import net.imglib2.type.numeric.real.DoubleType
import net.imglib2.util.Intervals