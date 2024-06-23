
// https://www.codeconvert.ai/java-to-groovy-converter used to convert Java code to groovy,
// using the additional command 'use def instead of explicit classes'

#@CommandService cs

def atlas = cs.run(AllenBrainAdultMouseAtlasCCF2017v3p1Command, true).get().getOutput("ba") as Atlas

// What channels do we have in this atlas ?
println atlas.getMap().getImagesKeys()
// The output will be: [Nissl, Ara, Label Borders, X, Y, Z, Left Right]

// Let's collect the first two channels
def nissl = atlas.getMap().getStructuralImages().get("Nissl")
def ara = atlas.getMap().getStructuralImages().get("Ara")

// Let's display one channel
def bvvNisslStack = BvvFunctions.show(nissl.asVolatile().getSpimSource())

// And the other in the same viewer
def bvvAraStack = BvvFunctions.show(ara.asVolatile().getSpimSource(), BvvOptions.options().addTo(bvvNisslStack.getBvvHandle()))

// Let's change the display settings to make this look nicer
bvvNisslStack.setDisplayRange(0, 25000)
bvvAraStack.setDisplayRange(0, 350)

bvvNisslStack.setColor(new ARGBType(ARGBType.rgba(0, 128, 255, 0)))
bvvAraStack.setColor(new ARGBType(ARGBType.rgba(220, 250, 0, 0)))


import bvv.vistools.BvvFunctions
import bvv.vistools.BvvOptions
import ch.epfl.biop.atlas.mouse.allen.ccfv3p1.command.AllenBrainAdultMouseAtlasCCF2017v3p1Command
import ch.epfl.biop.atlas.struct.Atlas
import net.imglib2.type.numeric.ARGBType
import org.scijava.command.CommandService

