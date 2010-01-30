package sketch.compiler.smt.solvers;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;

import sketch.compiler.ast.core.TempVarGen;
import sketch.compiler.dataflow.recursionCtrl.RecursionControl;
import sketch.compiler.main.seq.SMTSketchOptions;
import sketch.compiler.smt.SolverFailedException;
import sketch.compiler.smt.partialeval.FormulaPrinter;
import sketch.compiler.smt.partialeval.NodeToSmtVtype;
import sketch.compiler.smt.partialeval.SmtValueOracle;
import sketch.compiler.smt.smtlib.SMTLIBTranslatorBV;
import sketch.compiler.solvers.SolutionStatistics;
import sketch.util.ProcessStatus;
import sketch.util.Stopwatch;
import sketch.util.SynchronousTimedProcess;

public class Z3BVBackend extends SMTBackend {

	String solverInputFile;
	
	public Z3BVBackend(SMTSketchOptions options, String tmpFilePath,
			RecursionControl rcontrol, TempVarGen varGen, boolean tracing) throws IOException {
		super(options, tmpFilePath, rcontrol, varGen, tracing);

	}

	@Override
	public SolutionStatistics solve(NodeToSmtVtype formula) throws IOException, InterruptedException {
		
		Stopwatch watch = new Stopwatch();
		watch.start();
		ProcessStatus run = getSolverProcess().run(true);
		watch.stop();
		
		String solverOutput = run.out;
		String solverError = run.err;
		
		if (solverError.contains("ERROR:")) {
			throw new SolverFailedException(solverOutput + "\n" + solverError);	
		}
		
		Z3SolutionStatistics stat = new Z3SolutionStatistics(run.out, run.err);
		
		mOracle = createValueOracle();
		mOracle.linkToFormula(formula);
		
		if (stat.success) {
			LineNumberReader lir = new LineNumberReader(new StringReader(run.out));
			getOracle().loadFromStream(lir);
			lir.close();
		}
	
		return stat;
	}

	@Override
	protected SynchronousTimedProcess createSolverProcess() throws IOException {
		String z3Path = this.options.smtOpts.solverpath;
		SynchronousTimedProcess stp = new SynchronousTimedProcess(options.solverOpts.timeout,
				z3Path, this.getTmpFilePath(), "/m", "/st");
		return stp;
	}

	@Override
	public OutputStream createStreamToSolver() throws IOException {
		FileOutputStream fos = new FileOutputStream(this.getTmpFilePath());
		return fos;
	}

	@Override
	public SmtValueOracle createValueOracle() {
		return new Z3ManualParseOracle2_0(mTrans);
	}

	@Override
    protected FormulaPrinter createFormulaPrinterInternal(NodeToSmtVtype formula,
            PrintStream ps)
    {
        return new SMTLIBTranslatorBV(formula, ps, mIntNumBits);
    }

}
