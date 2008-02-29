package streamit.frontend;

import streamit.frontend.CommandLineParamManager.POpts;
import streamit.frontend.experimental.deadCodeElimination.EliminateDeadCode;
import streamit.frontend.experimental.eliminateTransAssign.EliminateTransAssns;
import streamit.frontend.experimental.preprocessor.FlattenStmtBlocks;
import streamit.frontend.experimental.preprocessor.SimplifyVarNames;
import streamit.frontend.nodes.Program;
import streamit.frontend.passes.AssembleInitializers;
import streamit.frontend.passes.AtomizeStatements;
import streamit.frontend.passes.ConstantReplacer;
import streamit.frontend.passes.NumberStatements;
import streamit.frontend.passes.ProtectArrayAccesses;
import streamit.frontend.passes.SemanticChecker;
import streamit.frontend.solvers.CounterExample;
import streamit.frontend.solvers.SATSynthesizer;
import streamit.frontend.solvers.SpinVerifier;
import streamit.frontend.solvers.Synthesizer;
import streamit.frontend.solvers.Verifier;
import streamit.frontend.stencilSK.EliminateStarStatic;
import streamit.frontend.stencilSK.SimpleCodePrinter;
import streamit.frontend.stencilSK.StaticHoleTracker;
import streamit.frontend.tosbit.RandomValueOracle;
import streamit.frontend.tosbit.ValueOracle;



public class ToPSbitII extends ToSBit {

	public ToPSbitII(String[] args){
		super(args);
	}

	public void run() {
		parseProgram();

		prog = (Program)prog.accept(new ConstantReplacer(params.varValues("D")));
		//dump (prog, "After replacing constants:");
		if (!SemanticChecker.check(prog))
			throw new IllegalStateException("Semantic check failed");

		prog=preprocessProgram(prog); // perform prereq transformations
		//dump (prog, "After preprocessing");
		// RenameBitVars is buggy!! prog = (Program)prog.accept(new RenameBitVars());
		// if (!SemanticChecker.check(prog))
		//	throw new IllegalStateException("Semantic check failed");

		if (prog == null)
			throw new IllegalStateException();

		synthVerifyLoop();
		finalCode = postprocessProgram (prog);
		generateCode(finalCode);

		System.out.println("[PSKETCH] DONE!");
	}

	public void synthVerifyLoop(){
		lowerIRToJava();

		Synthesizer synth = createSynth(prog);
		Verifier verif = createVerif(prog);

		ValueOracle ora = randomOracle(prog);
		boolean success = false;
		do{
			CounterExample cex = verif.verify( ora );
			if(cex == null){
				success = true;
				break;
				//we are done;
			}

			ora = synth.nextCandidate(cex);
			if(ora == null){
				success = false;
				break;
			}
		}while(true);

		if (!success) {
			System.err.println ("Whoops -- couldn't synthesize sketch.");
			System.exit (1);
			// TODO: real error message
		}

		oracle = ora;
	}

	protected Program preprocessProgram(Program lprog) {
		lprog = super.preprocessProgram(lprog);
		lprog = (Program) lprog.accept (new AtomizeStatements(varGen));
		return lprog;
	}

	public void lowerIRToJava() {
		super.lowerIRToJava();
		prog = (Program) prog.accept(new ProtectArrayAccesses(varGen));
		//prog = (Program) prog.accept(new SpinPreprocessor(varGen));
		prog = (Program) prog.accept(new NumberStatements());
	}

	public Program postprocessProgram (Program p) {
		p = (Program) p.accept (new EliminateStarStatic (oracle));

		// TODO: these passes may not be semantically valid under concurrency

		p = (Program)p.accept(new FlattenStmtBlocks());
		p = (Program)p.accept(new EliminateTransAssns());
		//p = (Program)p.accept(new EliminateDeadCode(params.hasFlag("keepasserts")));
		p = (Program)p.accept(new SimplifyVarNames());
		p = (Program)p.accept(new AssembleInitializers());

		return p;
	}

	public void generateCode (Program p) {
		p.accept (new SimpleCodePrinter ());
	}

	public Synthesizer createSynth(Program p){
		return new SATSynthesizer(p, params, internalRControl(), varGen );
	}

	public Verifier createVerif(Program p){
		boolean debug = params.flagValue ("verbosity") >= 3;
		boolean cleanup = !params.hasFlag ("keeptmpfiles");
		return new SpinVerifier (varGen, p, debug, cleanup);
	}

	public ValueOracle randomOracle(Program p){
		return new RandomValueOracle (new StaticHoleTracker(varGen));
	}

	protected void setCommandLineParams(){
		super.setCommandLineParams();
		params.setAllowedParam("schedlen", new POpts(POpts.NUMBER,
				"--schedlen  n \t Sets the length of the schedule for the parallel sections to n.",
				"10", null) );

		params.setAllowedParam("locklen", new POpts(POpts.NUMBER,
				"--locklen  n \t This is another one of those parameters that have to do with the way\n" +
				"             \t things are implemented. The locks array has to be of a static size. \n" +
				"             \t When you lock on a pointer, the pointer is transformed based on some \n" +
				"             \t strange function, and the resulting value is used to index the lock array. \n" +
				"             \t If that index is out of bounds, your sketch will not resolve, so you use this \n" +
				"             \t parameter to make that lock array larger.",
				"10", null) );
	}

	public static void main(String[] args)
	{
		new ToPSbitII (args).run();
		System.exit(0);
	}
}
