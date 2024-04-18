package ch.epfl.biop.test;

/**
 * Simple tests of potential arithmetic simplification that I think
 * a JIT compiler should be able to do:
 * - Multiplications by ones
 * - Additions with zeros...
 *
 * My wish is that special cases matrices computations could be auto-simplified.
 * (a translation matrix is only 3 additions in 3D)
 * Apparently we're not there yet, or simply I don't know how to trigger the magic
 *
 * I tried OpenJDK 1.8, OpenJDK 18, Graalvm-ce-17, none of them were able to
 * discard what appears to be no-ops
 *
 * Due to the simplicity of the code, please re-use and modify as you wish
 * @author Nicolas Chiaruttini, 25th July 2022
 */
public class JITTester {

    public static void main(String... args) {
        final int nRepetitions = 100_000_000;
        final double factor1 = 1.0d;
        final double factor1p1 = 1.1d;
        double value;

        System.out.println("----------------------");
        System.out.println("Repeating "+nRepetitions+" multiplication by factor1 = 1.0, a final variable");
        System.out.println("The code is put in the main method");
        System.out.println("This is overall is No op, and should be super fast");
        tic();
        value = 0.0d;
        for (int i=0;i<nRepetitions;i++) {
            for (int j=0;j<20;j++) {
                value = value * factor1;
            }
        }
        System.out.println("Result = "+value);
        toc();

        double nonFinalFactor1 = 1.0;
        System.out.println("----------------------");
        System.out.println("Repeating "+nRepetitions+" multiplication by nonFinalFactor =  1.0, a non final variable");
        System.out.println("The code is put in the main method");
        System.out.println("This is overall is No op, and should be super fast");
        tic();
        value = 0.0d;
        for (int i=0;i<nRepetitions;i++) {
            for (int j=0;j<20;j++) {
                value = value * nonFinalFactor1;
            }
        }
        System.out.println("Result = "+value);
        toc();

        System.out.println("----------------------");
        System.out.println("Repeating "+nRepetitions+" multiplication by 1.1");
        System.out.println("The code is put in the main method");
        System.out.println("This is NOT a no op, and thus can only be slow");
        tic();
        value = 0.0d;
        for (int i=0;i<nRepetitions;i++) {
            for (int j=0;j<20;j++) {
                value = value * factor1p1;
            }
        }
        System.out.println("Result = "+value);
        toc();

        System.out.println("----------------------");
        System.out.println("Repeating "+nRepetitions+" multiplication by factor1 = 1.0d");
        System.out.println("Function called = repeatMultiply");
        System.out.println("This is overall is No op, and should be super fast");
        tic();
        value = 0.0d;
        repeatMultiply(nRepetitions, value, factor1);
        toc();

        System.out.println("----------- ADDITIONS");

        final double number0 = 0.0;
        System.out.println("----------------------");
        System.out.println("Repeating "+nRepetitions+" additions by number0 = 0.0, a final variable");
        System.out.println("The code is put in the main method");
        System.out.println("This is overall is No op, and should be super fast");
        tic();
        value = 0.0d;
        for (int i=0;i<nRepetitions;i++) {
            for (int j=0;j<20;j++) {
                value = value + number0;
            }
        }
        System.out.println("Result = "+value);
        toc();

        System.out.println("----------------------");
        System.out.println("Repeating "+nRepetitions+" additions by 0.0, explicitely written in the code");
        System.out.println("The code is put in the main method");
        System.out.println("This is overall is No op, and should be super fast");
        tic();
        value = 0.0d;
        for (int i=0;i<nRepetitions;i++) {
            for (int j=0;j<20;j++) {
                value = value + 0d;
            }
        }
        System.out.println("Result = "+value);
        toc();

    }

    public static void repeatMultiply(int nRepetitions, double value, final double multFactor) {
        for (int i=0;i<nRepetitions;i++) {
            for (int j=0;j<20;j++) {
                value = value * multFactor;
            }
        }
        System.out.println("Result = "+value);
    }

    static long startTime;

    public static void tic() {
        startTime = System.nanoTime();
    }

    public static void toc() {
        long stopTime = System.nanoTime();
        System.out.println("Elapsed time \t"+((stopTime-startTime)/1e6)+"\t ms");
    }

}

/**
 * Results on my machines with graalvm-ce-17\bin\java.exe, only one situation leads to optimisation
 * ----------------------
 * Repeating 100000000 multiplication by factor1 = 1.0, a final variable
 * The code is put in the main method
 * This is overall is No op, and should be super fast
 * Result = 0.0
 * Elapsed time 	64.8528	 ms
 * ----------------------
 * Repeating 100000000 multiplication by nonFinalFactor =  1.0, a non final variable
 * The code is put in the main method
 * This is overall is No op, and should be super fast
 * Result = 0.0
 * Elapsed time 	1904.0471	 ms
 * ----------------------
 * Repeating 100000000 multiplication by 1.1
 * The code is put in the main method
 * This is NOT a no op, and thus can only be slow
 * Result = 0.0
 * Elapsed time 	1855.0064	 ms
 * ----------------------
 * Repeating 100000000 multiplication by factor1 = 1.0d
 * Function called = repeatMultiply
 * This is overall is No op, and should be super fast
 * Result = 0.0
 * Elapsed time 	1815.1354	 ms
 * ----------- ADDITIONS
 * ----------------------
 * Repeating 100000000 additions by number0 = 0.0, a final variable
 * The code is put in the main method
 * This is overall is No op, and should be super fast
 * Result = 0.0
 * Elapsed time 	1812.0575	 ms
 * ----------------------
 * Repeating 100000000 additions by 0.0, explicitely written in the code
 * The code is put in the main method
 * This is overall is No op, and should be super fast
 * Result = 0.0
 * Elapsed time 	1821.8383	 ms
 */
