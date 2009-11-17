package sketch.compiler.smt.stp;

import java.io.IOException;

import sketch.compiler.CommandLineParamManager;
import sketch.compiler.ast.core.TempVarGen;
import sketch.compiler.dataflow.recursionCtrl.RecursionControl;
import sketch.compiler.smt.SMTTranslator;
import sketch.compiler.smt.smtlib.SMTLIBTranslator;
import sketch.util.SynchronousTimedProcess;

public class StpSmtlibBackend extends STPBackend {
	private final static boolean USE_FILE_SYSTEM = true;
	
	public StpSmtlibBackend(CommandLineParamManager params, String tmpFilePath,
			RecursionControl rcontrol, TempVarGen varGen, boolean tracing)
			throws IOException {
		super(params, tmpFilePath, rcontrol, varGen, tracing);
	}

	@Override
	protected SMTTranslator createSMTTranslator() {
		return new SMTLIBTranslator(mIntNumBits);
	}
	
	@Override
	protected SynchronousTimedProcess createSolverProcess() throws IOException {
		String command;
		if (USE_FILE_SYSTEM) {
			command = params.sValue("smtpath") + " -m -p -s" + " " + getTmpFilePath();
		} else {
			command = params.sValue("smtpath") + " -m -p -s"; 
		}
		String[] commandLine = command.split(" ");
		return new SynchronousTimedProcess(params.flagValue("timeout"), commandLine);
	}
	
	
}