/*
 * Copyright 2003 by the Massachusetts Institute of Technology.
 *
 * Permission to use, copy, modify, and distribute this
 * software and its documentation for any purpose and without
 * fee is hereby granted, provided that the above copyright
 * notice appear in all copies and that both that copyright
 * notice and this permission notice appear in supporting
 * documentation, and that the name of M.I.T. not be used in
 * advertising or publicity pertaining to distribution of the
 * software without specific, written prior permission.
 * M.I.T. makes no representations about the suitability of
 * this software for any purpose.  It is provided "as is"
 * without express or implied warranty.
 */

package sketch.compiler.main.seq;

import java.io.FileWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sketch.compiler.CommandLineParamManager;
import sketch.compiler.Directive;
import sketch.compiler.CommandLineParamManager.OptionNotRecognziedException;
import sketch.compiler.CommandLineParamManager.POpts;
import sketch.compiler.Directive.OptionsDirective;
import sketch.compiler.ast.core.FEReplacer;
import sketch.compiler.ast.core.Program;
import sketch.compiler.ast.core.StreamSpec;
import sketch.compiler.ast.core.TempVarGen;
import sketch.compiler.ast.core.exprs.ExprStar;
import sketch.compiler.ast.core.typs.Type;
import sketch.compiler.ast.core.typs.TypePrimitive;
import sketch.compiler.ast.core.typs.TypeStruct;
import sketch.compiler.codegenerators.NodesToC;
import sketch.compiler.codegenerators.NodesToCTest;
import sketch.compiler.codegenerators.NodesToH;
import sketch.compiler.dataflow.cflowChecks.PerformFlowChecks;
import sketch.compiler.dataflow.deadCodeElimination.EliminateDeadCode;
import sketch.compiler.dataflow.eliminateTransAssign.EliminateTransAssns;
import sketch.compiler.dataflow.preprocessor.FlattenStmtBlocks;
import sketch.compiler.dataflow.preprocessor.PreprocessSketch;
import sketch.compiler.dataflow.preprocessor.SimplifyVarNames;
import sketch.compiler.dataflow.preprocessor.TypeInferenceForStars;
import sketch.compiler.dataflow.recursionCtrl.AdvancedRControl;
import sketch.compiler.dataflow.recursionCtrl.DelayedInlineRControl;
import sketch.compiler.dataflow.recursionCtrl.RecursionControl;
import sketch.compiler.dataflow.simplifier.ScalarizeVectorAssignments;
import sketch.compiler.main.PlatformLocalization;
import sketch.compiler.parser.StreamItParser;
import sketch.compiler.passes.lowering.*;
import sketch.compiler.passes.lowering.ProtectArrayAccesses.FailurePolicy;
import sketch.compiler.passes.preprocessing.BitTypeRemover;
import sketch.compiler.passes.preprocessing.BitVectorPreprocessor;
import sketch.compiler.passes.preprocessing.ForbidStarsInFieldDecls;
import sketch.compiler.passes.preprocessing.SimplifyExpressions;
import sketch.compiler.passes.printers.SimpleCodePrinter;
import sketch.compiler.solvers.SATBackend;
import sketch.compiler.solvers.SolutionStatistics;
import sketch.compiler.solvers.constructs.AbstractValueOracle;
import sketch.compiler.solvers.constructs.StaticHoleTracker;
import sketch.compiler.solvers.constructs.ValueOracle;
import sketch.compiler.stencilSK.EliminateStarStatic;
import sketch.compiler.stencilSK.preprocessor.ReplaceFloatsWithBits;
import sketch.util.ControlFlowException;
import sketch.util.Pair;



/**
 * Convert StreamIt programs to legal Java code.  This is the main
 * entry point for the StreamIt syntax converter.  Running it as
 * a standalone program reads the list of files provided on the
 * command line and produces equivalent Java code on standard
 * output or the file named in the <tt>--output</tt> command-line
 * parameter.
 *
 * @author  David Maze &lt;dmaze@cag.lcs.mit.edu&gt;
 * @version $Id$
 */
public class SequentialSketchMain
{

	// protected final CommandLineParams params;
	protected Program beforeUnvectorizing=null;

	protected final CommandLineParamManager params =  CommandLineParamManager.getParams();


	public SequentialSketchMain(String[] args){
		this.setCommandLineParams();
		params.loadParams(args);
	}

	public boolean isParallel () {
		return false;
	}

/**
 * This function produces a recursion control that is used by all the user visible transformations.
 * @return
 */
	public RecursionControl visibleRControl(){
		return visibleRControl (prog);
	}

	public static RecursionControl visibleRControl (Program p) {
		// return new BaseRControl(params.inlineAmt);
		return new AdvancedRControl(CommandLineParamManager.getParams().flagValue("branchamnt"), CommandLineParamManager.getParams().flagValue("inlineamnt"), p);
	}

	/**
	 * This function produces a recursion control that is used by all transformations that are not user visible.
	 * In particular, the conversion to boolean. By default it is the same as the visibleRControl.
	 * @return
	 */
	public RecursionControl internalRControl(){

		return new DelayedInlineRControl(0, 0);
	}


	/**
	 * Generate a Program object that includes built-in structures
	 * and streams with code, but no user code.
	 *
	 * @returns a StreamIt program containing only built-in code
	 */
	public static Program emptyProgram()
	{
		List<StreamSpec> streams = new java.util.ArrayList<StreamSpec>();
		List<TypeStruct> structs = new java.util.ArrayList<TypeStruct>();

		// Complex structure type:
		List<String> fields = new java.util.ArrayList<String>();
		List<Type> ftypes = new java.util.ArrayList<Type>();

		// We don't support the Complex type in SKETCH
		if (false) {
			Type floattype = TypePrimitive.floattype ;
			fields.add("real");
			ftypes.add(floattype);
			fields.add("imag");
			ftypes.add(floattype);
			TypeStruct complexStruct =
				new TypeStruct(null, "Complex", fields, ftypes);
			structs.add(complexStruct);
		}

		return new Program(null, streams, structs);
	}

	/**
	 * Read, parse, and combine all of the StreamIt code in a list of
	 * files.  Reads each of the files in <code>inputFiles</code> in
	 * turn and runs <code>sketch.compiler.StreamItParserFE</code>
	 * over it.  This produces a
	 * <code>sketch.compiler.nodes.Program</code> containing lists
	 * of structures and streams; combine these into a single
	 * <code>sketch.compiler.nodes.Program</code> with all of the
	 * structures and streams.
	 *
	 * @param inputFiles  list of strings naming the files to be read
	 * @returns a representation of the entire program, composed of the
	 *          code in all of the input files
	 * @throws java.io.IOException if an error occurs reading the input
	 *         files
	 * @throws antlr.RecognitionException if an error occurs parsing
	 *         the input files; that is, if the code is syntactically
	 *         incorrect
	 * @throws antlr.TokenStreamException if an error occurs producing
	 *         the input token stream
	 */
	public Pair<Program, Set<Directive>> parseFiles(List<String> inputFiles)
	throws java.io.IOException, antlr.RecognitionException, antlr.TokenStreamException
	{
		Program prog = emptyProgram();
		boolean useCpp = true;
		List<String> cppDefs = params.listValue ("def");
		Set<Directive> pragmas = new HashSet<Directive> ();

		for (String inputFile : inputFiles) {
			StreamItParser parser = new StreamItParser (inputFile, useCpp, cppDefs);
			Program pprog = parser.parse ();
			if (pprog==null)
				return null;

			List<StreamSpec> newStreams = new java.util.ArrayList<StreamSpec> ();
			List<TypeStruct> newStructs = new java.util.ArrayList<TypeStruct> ();
			newStreams.addAll(prog.getStreams());
			newStreams.addAll(pprog.getStreams());
			newStructs.addAll(prog.getStructs());
			newStructs.addAll(pprog.getStructs());
			pragmas.addAll (parser.getDirectives ());
			prog = new Program(null, newStreams, newStructs);
		}
		return new Pair<Program, Set<Directive>> (prog, pragmas);
	}

	/**
	 * Transform front-end code to have the Java syntax.  Goes through
	 * a series of lowering passes to convert an IR tree from the
	 * "new" syntax to the "old" Java syntax understood by the main
	 * StreamIt compiler.  Conversion directed towards the StreamIt
	 * Java library, as opposed to the compiler, has slightly
	 * different output, mostly centered around phased filters.
	 *
	 * @param libraryFormat  true if the program is being converted
	 *        to run under the StreamIt Java library
	 * @param varGen  object to generate unique temporary variable names
	 * @returns the converted IR tree
	 */
	public void lowerIRToJava()
	{
		prog = (Program)prog.accept(new EliminateBitSelector(varGen));

		prog = (Program)prog.accept(new EliminateArrayRange(varGen));
		beforeUnvectorizing = prog;
		
		prog = (Program) prog.accept(new ReplaceFloatsWithBits());
		
		// prog = (Program)prog.accept (new BoundUnboundedLoops (varGen, params.flagValue ("unrollamnt")));
		
		
		//dump (prog, "bef fpe:");
		
		prog = (Program)prog.accept(new MakeBodiesBlocks());
		// dump (prog, "MBB:");
		prog = (Program)prog.accept(new EliminateStructs(varGen, params.flagValue("heapsize")));
		prog = (Program)prog.accept(new DisambiguateUnaries(varGen));
		// dump (prog, "After eliminating structs:");
		prog = (Program)prog.accept(new EliminateMultiDimArrays());
		//dump (prog, "After second elimination of multi-dim arrays:");
		prog = (Program)prog.accept(new ExtractRightShifts(varGen));
		prog = (Program)prog.accept(new ExtractVectorsInCasts(varGen));
		//dump (prog, "Extract Vectors in Casts:");
		prog = (Program)prog.accept(new SeparateInitializers());
		//dump (prog, "SeparateInitializers:");
		//prog = (Program)prog.accept(new NoRefTypes());
		prog = (Program)prog.accept(new ScalarizeVectorAssignments(varGen, true));
		// dump (prog, "ScalarizeVectorAssns");

		// By default, we don't protect array accesses in SKETCH
		if ("assertions".equals (params.sValue ("arrayOOBPolicy")))
			prog = (Program) prog.accept(new ProtectArrayAccesses(
					FailurePolicy.ASSERTION, varGen));

		// dump (prog, "After protecting array accesses.");
		
		if(params.flagEquals("showphase", "lowering")) dump(prog, "Lowering the code previous to Symbolic execution.");


		prog = (Program)prog.accept(new EliminateNestedArrAcc());
		//dump (prog, "After lowerIR:");
	}


	protected TempVarGen varGen = new TempVarGen();
	protected Program prog = null;
	protected AbstractValueOracle oracle;
	protected Program finalCode;

	public Program parseProgram(){
		try
		{
			Pair<Program, Set<Directive>> res = parseFiles(params.inputFiles);
			prog = res.getFirst ();
			processDirectives (res.getSecond ());
		}
		catch (Exception e)
		{
			//e.printStackTrace(System.err);
			throw new RuntimeException(e);
		}

		if (prog == null)
		{
			System.err.println("Compilation didn't generate a parse tree.");
			throw new IllegalStateException();
		}
		return prog;

	}

	protected void processDirectives (Set<Directive> D) {
		for (Directive d : D)
			if (d instanceof OptionsDirective) {
				try {
					params.loadParams (((OptionsDirective) d).options ());
				} catch (OptionNotRecognziedException e) {
					// ignore any unrecognized pragma
				}
			}
	}

	protected Program preprocessProgram(Program lprog) {
		boolean useInsertEncoding = params.flagEquals ("reorderEncoding", "exponential");
		//invoke post-parse passes

		lprog.accept(new ForbidStarsInFieldDecls());		
		
		//dump (lprog, "before:");
		lprog = (Program)lprog.accept(new SeparateInitializers ());
		lprog = (Program)lprog.accept(new BlockifyRewriteableStmts ());

		lprog = (Program)lprog.accept(new ExtractComplexLoopConditions (varGen));
		lprog = (Program)lprog.accept(new EliminateRegens(varGen));
		//dump (lprog, "~regens");
		
		//dump (lprog, "extract clc");
		// lprog = (Program)lprog.accept (new BoundUnboundedLoops (varGen, params.flagValue ("unrollamnt")));
		
		// prog = (Program)prog.accept(new NoRefTypes());
		lprog = (Program)lprog.accept(new EliminateReorderBlocks(varGen, useInsertEncoding));
		//dump (lprog, "~reorderblocks:");
		lprog = (Program)lprog.accept(new EliminateInsertBlocks(varGen));
		//dump (lprog, "~insertblocks:");		
		lprog = (Program)lprog.accept(new FunctionParamExtension(true));
		//dump (lprog, "fpe:");
		lprog = (Program)lprog.accept(new DisambiguateUnaries(varGen));
		//dump (lprog, "tifs:");
		lprog = (Program)lprog.accept(new TypeInferenceForStars());
		//dump (lprog, "tifs:");
		
		lprog.accept(new PerformFlowChecks());
		
		
		lprog = (Program) lprog.accept (new EliminateMultiDimArrays ());
		// dump (lprog, "After first elimination of multi-dim arrays:");
		lprog = (Program) lprog.accept( new PreprocessSketch( varGen, params.flagValue("unrollamnt"), visibleRControl() ) );
		if(params.flagEquals("showphase", "preproc")) dump (lprog, "After Preprocessing");

		return lprog;
	}

	public SolutionStatistics partialEvalAndSolve(){
		lowerIRToJava();
		SATBackend solver = new SATBackend(params, internalRControl(), varGen);
		
		if(params.hasFlag("trace")){
			solver.activateTracing();
		}
		backendParameters(solver.commandLineOptions);
		solver.partialEvalAndSolve(prog);
		
		oracle =solver.getOracle();
		return solver.getLastSolutionStats();
	}

	public void eliminateStar(){
	    EliminateStarStatic eliminate_star = new EliminateStarStatic(oracle);
		finalCode=(Program)beforeUnvectorizing.accept(eliminate_star);
		if (params.hasFlag("outputxml")){
		    eliminate_star.dump_xml();
        }
		dump(finalCode, "after elim star");
		finalCode=(Program)finalCode.accept(new PreprocessSketch( varGen, params.flagValue("unrollamnt"), visibleRControl(), true ));
		dump(finalCode, "After partially evaluating generated code.");
		finalCode = (Program)finalCode.accept(new FlattenStmtBlocks());
		if(params.flagEquals("showphase", "postproc")) 
			dump(finalCode, "After Flattening.");
		finalCode = (Program)finalCode.accept(new EliminateTransAssns());
		//System.out.println("=========  After ElimTransAssign  =========");
		if(params.flagEquals("showphase", "taelim")) 
			dump(finalCode, "After Eliminating transitive assignments.");
		finalCode = (Program)finalCode.accept(new EliminateDeadCode(params.hasFlag("keepasserts")));
		//dump(finalCode, "After Dead Code elimination.");
		//System.out.println("=========  After ElimDeadCode  =========");
		finalCode = (Program)finalCode.accept(new SimplifyVarNames());
		finalCode = (Program)finalCode.accept(new AssembleInitializers());
		if(params.flagEquals("showphase", "final")) 
			dump(finalCode, "After Dead Code elimination.");
	}

	protected String getOutputFileName() {
		String resultFile = params.sValue("outputprogname");
		if(resultFile==null) {
			resultFile=params.inputFiles.get(0);
		}
		if(resultFile.lastIndexOf("/")>=0)
			resultFile=resultFile.substring(resultFile.lastIndexOf("/")+1);
		if(resultFile.lastIndexOf("\\")>=0)
			resultFile=resultFile.substring(resultFile.lastIndexOf("\\")+1);
		if(resultFile.lastIndexOf(".")>=0)
			resultFile=resultFile.substring(0,resultFile.lastIndexOf("."));
		if(resultFile.lastIndexOf(".sk")>=0)
			resultFile=resultFile.substring(0,resultFile.lastIndexOf(".sk"));
		return resultFile;
	}

	protected void outputCCode() {


		String resultFile = getOutputFileName();
		String hcode = (String)finalCode.accept(new NodesToH(resultFile));
		String ccode = (String)finalCode.accept(new NodesToC(varGen,resultFile));
		
		if(!params.hasFlag("outputcode")){
			finalCode.accept( new SimpleCodePrinter() );
			//System.out.println(hcode);
			//System.out.println(ccode);
		}else{
			try{
				{
					Writer outWriter = new FileWriter(params.sValue("outputdir") +resultFile+".h");
					outWriter.write(hcode);
					outWriter.flush();
					outWriter.close();
					outWriter = new FileWriter(params.sValue("outputdir")+resultFile+".cpp");
					outWriter.write(ccode);
					outWriter.flush();
					outWriter.close();
				}
				if( params.hasFlag("outputtest")  ) {
					String testcode=(String)beforeUnvectorizing.accept(new NodesToCTest(resultFile));
					Writer outWriter = new FileWriter(params.sValue("outputdir")+resultFile+"_test.cpp");
					outWriter.write(testcode);
					outWriter.flush();
					outWriter.close();
				}
				if( params.hasFlag("outputtest") ) {
					Writer outWriter = new FileWriter(params.sValue("outputdir")+"script");
					outWriter.write("#!/bin/sh\n");
					outWriter.write("if [ -z \"$SKETCH_HOME\" ];\n" +
							"then\n" +
							"echo \"You need to set the \\$SKETCH_HOME environment variable to be the path to the SKETCH distribution; This is needed to find the SKETCH header files needed to compile your program.\" >&2;\n" +
							"exit 1;\n" +
							"fi\n");
					outWriter.write("g++ -I \"$SKETCH_HOME/include\" -o "+resultFile+" "+resultFile+".cpp "+resultFile+"_test.cpp\n");

					outWriter.write("./"+resultFile+"\n");
					outWriter.flush();
					outWriter.close();
				}
			}
			catch (java.io.IOException e){
				throw new RuntimeException(e);
			}
		}
	}
	
	public String benchmarkName(){
		String rv = "";
		boolean f = true;
		for(String s : params.inputFiles){
			if(!f){rv += "_";}
			rv += s;			
			f = false;
		}
		Map<String, Integer> m =  params.varValues("D");
		if(m != null){
			for(Map.Entry<String, Integer> e :m.entrySet()){
				rv+= "_";
				rv += e.getKey() + "=" + e.getValue();
			}
		}
		return rv;
	}
	

	protected boolean isSketch (Program p) {
		class hasHoles extends FEReplacer {
			public Object visitExprStar (ExprStar es) {
				throw new ControlFlowException ("yes");
			}
		}
		try {  p.accept (new hasHoles ());  return false;  }
		catch (ControlFlowException cfe) {  return true;  }
	}

	protected void setCommandLineParams(){

		params.setAllowedParam("D", new POpts(POpts.VVAL,
				"--D VAR val    \t If the program contains a global variable VAR, it sets its value to val.",
				null, null));

		params.setAllowedParam("unrollamnt", new POpts(POpts.NUMBER,
				"--unrollamnt n \t It sets the unroll ammount for loops to n.",
				"8", null) );

		params.setAllowedParam("inlineamnt", new POpts(POpts.NUMBER,
				"--inlineamnt n \t Bounds inlining to n levels of recursion, so" +
				"\n\t\t each function can appear at most n times in the stack.",
				"5", null) );

		params.setAllowedParam("heapsize", new POpts(POpts.NUMBER,
				"--heapsize n \t Size of the heap for each object. This is the maximum" +
				"\n\t\t number of objects of a given type that the program may allocate.",
				"11", null) );

		params.setAllowedParam("branchamnt", new POpts(POpts.NUMBER,
				"--branchamnt n \t This flag is also used for recursion control. " +
				"\n\t\t It bounds inlining based on the idea that if a function calls " +
				"\n\t\t itself recureively ten times, we want to inline it less than a function" +
				"\n\t\t that calls itself recursively only once. In this case, n is the " +
				"\n\t\t maximum value of the branching factor, which is the number of times" +
				"\n\t\t a function calls itself recursively, times the amount of inlining. ",
				"15", null) );

		params.setAllowedParam("incremental", new POpts(POpts.NUMBER,
				"--incremental n\t Tells the solver to incrementally grow the size of integer holes from 1 to n bits.",
				"5", null) );

		params.setAllowedParam("timeout", new POpts(POpts.NUMBER,
				"--timeout min  \t Kills the solver after min minutes.",
				"0", null) );

		params.setAllowedParam("fakesolver", new POpts(POpts.FLAG,
				"--fakesolver   \t This flag indicates that the SAT solver should not be invoked. " +
				"\n \t\t Instead the frontend should look for a solution file, and generate the code from that. " +
				"\n \t\t It is useful when working with sketches that take a long time to resolve" +
				"\n \t\t if one wants to play with different settings for code generation.",
				null, null) );

		params.setAllowedParam("seed", new POpts(POpts.NUMBER,
				"--seed s       \t Seeds the random number generator with s.",
				null, null) );

		params.setAllowedParam("verbosity", new POpts(POpts.NUMBER,
				"--verbosity n       \t Sets the level of verbosity for the output. 0 is quite mode 5 is the most verbose.",
				"1", null) );
		
		params.setAllowedParam("olevel", new POpts(POpts.NUMBER,
				"--olevel n       \t Sets the optimization level for the compiler.",
				"5", null) );

		params.setAllowedParam("cex", new POpts(POpts.FLAG,
				"--cex       \t Show the counterexample inputs produced by the solver (Equivalend to backend flag -showinputs).",
				null, null) );

		params.setAllowedParam("outputcode", new POpts(POpts.FLAG,
				"--outputcode   \t Use this flag if you want the compiler to produce C code.",
				null, null) );

		params.setAllowedParam("outputxml", new POpts(POpts.FLAG,
		            "--outputxml  \t Output the values of holes as XML", null, null));

		params.setAllowedParam("keepasserts", new POpts(POpts.FLAG,
				"--keepasserts   \t The synthesizer guarantees that all asserts will succeed." +
				"\n \t\t For this reason, all asserts are removed from generated code by default. However, " +
				"\n \t\t sometimes it is useful for debugging purposes to keep the assertions around.",
				null, null) );

		params.setAllowedParam("outputtest", new POpts(POpts.FLAG,
				"--outputtest   \t Produce also a harness to test the generated C code.",
				null, null) );

		params.setAllowedParam("outputdir", new POpts(POpts.STRING,
				"--outputdir dir\t Set the directory where you want the generated code to live.",
				"./", null) );

		params.setAllowedParam("outputprogname", new POpts(POpts.STRING,
				"--outputprogname name \t Set the name of the output C files." +
				"\n \t\t By default it is the name of the first input file.",
				null, null) );

		params.setAllowedParam("output", new POpts(POpts.STRING,
				"--output file  \t Temporary output file used to communicate " +
				"with backend solver.",
				PlatformLocalization.getLocalization().getDefaultTempFile(), null) );

		params.setAllowedParam("cegispath", new POpts(POpts.STRING,
				"--cegispath path\t Path to the 'cegis' binary, overriding default search paths",
				"", null) );



		params.setAllowedParam("forcecodegen", new POpts(POpts.FLAG,
				"--forcecodegen  \t Forces code generation. Even if the sketch fails to resolve, " +
				"                \t this flag will force the synthesizer to produce code from the latest known control values.",
				null, null) );

		params.setAllowedParam("keeptmpfiles", new POpts(POpts.FLAG,
				"--keeptmpfiles  \t Keep intermediate files. Useful for debugging the compiler.",
				null, null) );

		params.setAllowedParam("cbits", new POpts(POpts.NUMBER,
				"--cbits n      \t Specify the number of bits to use for integer holes.",
				"5", null) );

		params.setAllowedParam("inbits", new POpts(POpts.NUMBER,
				"--inbits n      \t Specify the number of bits to use for integer inputs.",
				"5", null) );


		params.setAllowedParam("trace", new POpts(POpts.FLAG,
				"--trace  \t Show a trace of the symbolic execution. Useful for debugging purposes.",
				null, null) );
		
		
		params.setAllowedParam("showExceptionstack", new POpts(POpts.FLAG,
				"--trace  \t In case of an exception, show the full stack, not just the debug message.",
				null, null) );
		
		params.setAllowedParam("reorderEncoding", new POpts(POpts.STRING,
				"--reorderEncoding  which \t How reorder blocks should be rewritten.  Current supported:\n" +
				"             \t * exponential -- use 'insert' blocks\n" +
				"             \t * quadratic -- use a loop of switch statements\n",
				"exponential", null) );

		params.setAllowedParam("def", new POpts(POpts.MULTISTRING,
				"--def        \t Vars to define for the C preprocessor.\n"+
				"             \t Consider also using the 'safer' option --D VAR value\n"+
				"             \t Example use:  '--def _FOO=1 --def _BAR=false ...'",
				null, null) );

		params.setAllowedParam("inc", new POpts(POpts.MULTISTRING,
				"--inc        \t Directory to search for include files.'",
				null, null) );
		
		Map<String, String> phases = new HashMap<String, String>();
		phases.put("preproc", " After preprocessing.");
		phases.put("lowering", " Previous to Symbolic execution.");
		phases.put("postproc", " After partially evaluating the generated code (ugly).");
		phases.put("taelim", " After eliminating transitive assignments (before cse, ugly).");
		phases.put("final", " After all optimizations.");
		params.setAllowedParam("showphase", new POpts(POpts.TOKEN,
				"--showphase OPT\t Show the partially evaluated code after the indicated phase of pre or post processing.",
				"5", phases) );

		Map<String, String> solvers = new HashMap<String, String>();
		solvers.put("MINI","MiniSat solver");
		solvers.put("ABC", "ABC solver");
		params.setAllowedParam("synth", new POpts(POpts.TOKEN,
				"--synth OPT\t SAT solver to use for synthesis.",
				"MINI", solvers) );
		params.setAllowedParam("verif", new POpts(POpts.TOKEN,
				"--verif OPT\t SAT solver to use for verification.",
				"MINI", solvers) );

		Map<String, String> failurePolicies = new HashMap<String, String> ();
		failurePolicies.put ("wrsilent_rdzero", "Read a zero, silently ignore writes");
		failurePolicies.put ("assertions", "Fail assertions for reads and writes");
		params.setAllowedParam ("arrayOOBPolicy", new POpts (POpts.TOKEN,
				"--arrayOOBPolicy policy \t What to do when an array access would be out\n"+
				"                        \t of bounds.",
				"assertions", failurePolicies));
	}


	protected Program doBackendPasses(Program prog) {
		if( false && params.hasFlag("outputcode") ) {
			prog=(Program) prog.accept(new AssembleInitializers());
			prog=(Program) prog.accept(new BitVectorPreprocessor(varGen));
			//prog.accept(new SimpleCodePrinter());
			prog=(Program) prog.accept(new BitTypeRemover(varGen));
			prog=(Program) prog.accept(new SimplifyExpressions());
		}
		return prog;
	}

	public void generateCode(){
		finalCode=doBackendPasses(finalCode);
		outputCCode();
	}

	public void run()
	{
		log(1, "Benchmark = " + benchmarkName());
		parseProgram();
		preprocAndSemanticCheck();
		
		oracle = new ValueOracle( new StaticHoleTracker(varGen)/* new SequentialHoleTracker(varGen) */);
		partialEvalAndSolve();
		eliminateStar();

		generateCode();
		log(1, "[SKETCH] DONE");

	}

	public void preprocAndSemanticCheck() {

		prog = (Program)prog.accept(new ConstantReplacer(params.varValues("D")));
		//dump (prog, "After replacing constants:");
		if (!SemanticChecker.check(prog, isParallel ()))
			throw new IllegalStateException("Semantic check failed");
		
		
		prog=preprocessProgram(prog); // perform prereq transformations
		//prog.accept(new SimpleCodePrinter());
		// RenameBitVars is buggy!! prog = (Program)prog.accept(new RenameBitVars());
		// if (!SemanticChecker.check(prog))
		//	throw new IllegalStateException("Semantic check failed");

		if (prog == null)
			throw new IllegalStateException();
		
	}

	protected void backendParameters(List<String> commandLineOptions){
		if( params.hasFlag("inbits") ){
			commandLineOptions.add("-overrideInputs");
			commandLineOptions.add( "" + params.flagValue("inbits") );
		}
		if( params.hasFlag("seed") ){
			commandLineOptions.add("-seed");
			commandLineOptions.add( "" + params.flagValue("seed") );
		}
		if( params.hasFlag("cex")){
			commandLineOptions.add("-showinputs");
		}
		if( params.hasFlag("verbosity") ){
			commandLineOptions.add("-verbosity");
			commandLineOptions.add( "" + params.flagValue("verbosity") );
		}
		if(params.hasFlag("synth")){
			commandLineOptions.add("-synth");
			commandLineOptions.add( "" + params.sValue("synth") );
		}
		if(params.hasFlag("verif")){
			commandLineOptions.add("-verif");
			commandLineOptions.add( "" + params.sValue("verif") );
		}
		if(params.flagEquals("arrayOOBPolicy", "assertions")){
			commandLineOptions.add("-assumebcheck");
		}
		if(params.hasFlag("olevel")){
			commandLineOptions.add("-olevel");
			commandLineOptions.add( "" + params.flagValue("olevel") );
		}
	}




	String solverErrorStr;

	protected void log (String msg) {  log (3, msg);  }
	protected void log (int level, String msg) {
		if (params.flagValue ("verbosity") >= level)
			System.out.println (msg);
	}

	public static void dump (Program prog) {
		dump (prog, "");
	}
	public static void dump (Program prog, String message) {
		System.out.println("=============================================================");
		System.out.println ("  ----- "+ message +" -----");
		prog.accept( new SimpleCodePrinter() );
		System.out.println("=============================================================");
	}

	public static void main(String[] args)
	{
        try {
            CommandLineParamManager.reset_singleton();
            new SequentialSketchMain(args).run();
        } catch (RuntimeException e) {
            System.err.println("[ERROR] [SKETCH] Failed with exception "
                    + e.getMessage());
            throw e;
        }
	}
}

