package sketch.compiler.cmdline;

import sketch.util.cli.CliAnnotatedOptionGroup;
import sketch.util.cli.CliParameter;

/**
 * Options for the frontend, such as where to find cegis.
 * @author gatoatigrado (nicholas tung) [email: ntung at ntung]
 * @license This file is licensed under BSD license, available at
 *          http://creativecommons.org/licenses/BSD/. While not required, if you
 *          make changes, please consider contributing back!
 */
public class FrontendOptions extends CliAnnotatedOptionGroup {
    public FrontendOptions() {
        super("fe", "miscellaneous frontend options");
    }

    @CliParameter(help = "Path to the 'cegis' binary, overriding default search paths.")
    public String cegisPath = null;
    @CliParameter(metavar = "VAR=val", inlinesep=",", 
            help = "If the program contains a global variable VAR, it sets its value to val.")
    public String[] def = new String[0];
    @CliParameter(help = "Directory to search for include files.")
    public String[] inc = new String[0];
    @CliParameter(help = "Forces code generation. Even if the sketch fails to resolve, "
            + "this flag will force the synthesizer to produce code from the latest known control values.")
    public boolean forceCodegen;
    @CliParameter(help = "The synthesizer guarantees that all asserts will succeed. "
            + "For this reason, all asserts are removed from generated code by default. "
            + "However, sometimes it is useful for debugging purposes to keep the assertions around.")
    public boolean keepAsserts;
    @CliParameter(help = "Keep intermediate files. Useful for debugging the compiler.")
    public boolean keepTmp;
    @CliParameter(help = "Temporary output file used to communicate with backend solver.")
    public String output = null;
    @CliParameter(help = "Use this flag if you want the compiler to produce C code.")
    public boolean outputCode;
    @CliParameter(help = "Set the name of the output C files. By default it is the "
            + "name of the first input file.")
    public String outputProgName;
    @CliParameter(help = "Output the values of holes as XML.")
    public boolean outputXml;
    @CliParameter(help = "Set the directory where you want the generated code to be written.")
    public String outputDir = "./";
    @CliParameter(help = "Produce also a harness to test the generated C code.")
    public boolean outputTest;
//    @CliParameter(help = "Enable Fortran output")
//    public boolean outputFortran;
}