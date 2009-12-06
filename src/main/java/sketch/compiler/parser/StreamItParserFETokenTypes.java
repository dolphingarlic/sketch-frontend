// $ANTLR 2.7.7 (2006-11-01): "StreamItParserFE.g" -> "StreamItParserFE.java"$

		package sketch.compiler.parser;

	import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import sketch.compiler.CommandLineParamManager;
import sketch.compiler.Directive;
import sketch.compiler.ast.core.FEContext;
import sketch.compiler.ast.core.FieldDecl;
import sketch.compiler.ast.core.FuncWork;
import sketch.compiler.ast.core.Function;
import sketch.compiler.ast.core.Parameter;
import sketch.compiler.ast.core.Program;
import sketch.compiler.ast.core.SplitterJoiner;
import sketch.compiler.ast.core.StreamSpec;
import sketch.compiler.ast.core.StreamType;
import sketch.compiler.ast.core.exprs.*;
import sketch.compiler.ast.core.stmts.*;
import sketch.compiler.ast.core.typs.Type;
import sketch.compiler.ast.core.typs.TypeArray;
import sketch.compiler.ast.core.typs.TypePortal;
import sketch.compiler.ast.core.typs.TypePrimitive;
import sketch.compiler.ast.core.typs.TypeStruct;
import sketch.compiler.ast.core.typs.TypeStructRef;
import sketch.compiler.ast.promela.stmts.StmtFork;
import sketch.compiler.passes.streamit_old.SJDuplicate;
import sketch.compiler.passes.streamit_old.SJRoundRobin;
import sketch.compiler.passes.streamit_old.SJWeightedRR;

public interface StreamItParserFETokenTypes {
	int EOF = 1;
	int NULL_TREE_LOOKAHEAD = 3;
	int TK_atomic = 4;
	int TK_fork = 5;
	int TK_insert = 6;
	int TK_into = 7;
	int TK_loop = 8;
	int TK_repeat = 9;
	int TK_new = 10;
	int TK_null = 11;
	int TK_reorder = 12;
	int TK_boolean = 13;
	int TK_float = 14;
	int TK_bit = 15;
	int TK_int = 16;
	int TK_void = 17;
	int TK_double = 18;
	int TK_complex = 19;
	int TK_struct = 20;
	int TK_ref = 21;
	int TK_if = 22;
	int TK_else = 23;
	int TK_while = 24;
	int TK_for = 25;
	int TK_switch = 26;
	int TK_case = 27;
	int TK_default = 28;
	int TK_break = 29;
	int TK_do = 30;
	int TK_continue = 31;
	int TK_return = 32;
	int TK_true = 33;
	int TK_false = 34;
	int TK_implements = 35;
	int TK_assert = 36;
	int TK_h_assert = 37;
	int TK_static = 38;
	int TK_include = 39;
	int TK_pragma = 40;
	int ARROW = 41;
	int WS = 42;
	int LINERESET = 43;
	int SL_COMMENT = 44;
	int ML_COMMENT = 45;
	int LPAREN = 46;
	int RPAREN = 47;
	int LCURLY = 48;
	int RCURLY = 49;
	int LSQUARE = 50;
	int RSQUARE = 51;
	int PLUS = 52;
	int PLUS_EQUALS = 53;
	int INCREMENT = 54;
	int MINUS = 55;
	int MINUS_EQUALS = 56;
	int DECREMENT = 57;
	int STAR = 58;
	int STAR_EQUALS = 59;
	int DIV = 60;
	int DIV_EQUALS = 61;
	int MOD = 62;
	int LOGIC_AND = 63;
	int LOGIC_OR = 64;
	int BITWISE_AND = 65;
	int BITWISE_OR = 66;
	int BITWISE_XOR = 67;
	int ASSIGN = 68;
	int EQUAL = 69;
	int NOT_EQUAL = 70;
	int LESS_THAN = 71;
	int LESS_EQUAL = 72;
	int MORE_THAN = 73;
	int MORE_EQUAL = 74;
	int QUESTION = 75;
	int COLON = 76;
	int SEMI = 77;
	int COMMA = 78;
	int DOT = 79;
	int BANG = 80;
	int LSHIFT = 81;
	int RSHIFT = 82;
	int NDVAL = 83;
	int NDVAL2 = 84;
	int SELECT = 85;
	int REGEN = 86;
	int CHAR_LITERAL = 87;
	int STRING_LITERAL = 88;
	int ESC = 89;
	int DIGIT = 90;
	int HQUAN = 91;
	int NUMBER = 92;
	int ID = 93;
	int TK_pipeline = 94;
	int TK_splitjoin = 95;
	int TK_feedbackloop = 96;
	int TK_sbox = 97;
	int TK_roundrobin = 98;
	int TK_duplicate = 99;
	int TK_portal = 100;
	int TK_handler = 101;
	int TK_pi = 102;
}
