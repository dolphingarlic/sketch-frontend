// $ANTLR 2.7.7 (20060906): "StreamItParserFE.g" -> "StreamItParserFE.java"$

package sketch.compiler.parser;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.Map;
import java.util.HashMap;

import sketch.compiler.Directive;
import sketch.compiler.ast.core.FEContext;
import sketch.compiler.ast.core.FieldDecl;
import sketch.compiler.ast.core.Function;
import sketch.compiler.ast.core.Parameter;
import sketch.compiler.ast.core.Program;
import sketch.compiler.ast.core.Annotation;
import sketch.compiler.ast.core.NameResolver;
import sketch.util.datastructures.HashmapList;
import sketch.compiler.ast.core.exprs.ExprStar.Kind;

import sketch.compiler.ast.core.Package;


import sketch.compiler.ast.core.exprs.*;
import sketch.compiler.ast.core.exprs.regens.*;
import sketch.compiler.ast.core.stmts.*;
import sketch.compiler.ast.core.typs.*;
import sketch.compiler.ast.cuda.exprs.*;
import sketch.compiler.ast.cuda.stmts.*;
import sketch.compiler.ast.cuda.typs.*;

import sketch.compiler.ast.promela.stmts.StmtFork;
import sketch.compiler.main.cmdline.SketchOptions;

import sketch.compiler.ast.spmd.stmts.StmtSpmdfork;

import static sketch.util.DebugOut.assertFalse;

public interface StreamItParserFETokenTypes {
	int EOF = 1;
	int NULL_TREE_LOOKAHEAD = 3;
	int TK_atomic = 4;
	int TK_fork = 5;
	int TK_insert = 6;
	int TK_into = 7;
	int TK_loop = 8;
	int TK_repeat = 9;
	int TK_minrepeat = 10;
	int TK_new = 11;
	int TK_null = 12;
	int TK_reorder = 13;
	int TK_assume = 14;
	int TK_hassert = 15;
	int TK_dassert = 16;
	int TK_boolean = 17;
	int TK_float = 18;
	int TK_bit = 19;
	int TK_int = 20;
	int TK_void = 21;
	int TK_double = 22;
	int TK_fun = 23;
	int TK_char = 24;
	int TK_struct = 25;
	int TK_ref = 26;
	int TK_adt = 27;
	int TK_if = 28;
	int TK_else = 29;
	int TK_while = 30;
	int TK_for = 31;
	int TK_switch = 32;
	int TK_case = 33;
	int TK_repeat_case = 34;
	int TK_default = 35;
	int TK_break = 36;
	int TK_do = 37;
	int TK_continue = 38;
	int TK_return = 39;
	int TK_true = 40;
	int TK_false = 41;
	int TK_parfor = 42;
	int TK_until = 43;
	int TK_by = 44;
	int TK_implements = 45;
	int TK_assert = 46;
	int TK_assert_max = 47;
	int TK_h_assert = 48;
	int TK_generator = 49;
	int TK_harness = 50;
	int TK_model = 51;
	int TK_fixes = 52;
	int TK_global = 53;
	int TK_serial = 54;
	int TK_spmdfork = 55;
	int TK_stencil = 56;
	int TK_include = 57;
	int TK_pragma = 58;
	int TK_package = 59;
	int TK_extends = 60;
	int TK_let = 61;
	int TK_precond = 62;
	int ARROW = 63;
	int LARROW = 64;
	int WS = 65;
	int LINERESET = 66;
	int SL_COMMENT = 67;
	int ML_COMMENT = 68;
	int LPAREN = 69;
	int RPAREN = 70;
	int LCURLY = 71;
	int RCURLY = 72;
	int LSQUARE = 73;
	int RSQUARE = 74;
	int PLUS = 75;
	int PLUS_EQUALS = 76;
	int INCREMENT = 77;
	int MINUS = 78;
	int MINUS_EQUALS = 79;
	int DECREMENT = 80;
	int STAR = 81;
	int STAR_EQUALS = 82;
	int DIV = 83;
	int DIV_EQUALS = 84;
	int MOD = 85;
	int LOGIC_AND = 86;
	int LOGIC_OR = 87;
	int BITWISE_AND = 88;
	int BITWISE_OR = 89;
	int BITWISE_XOR = 90;
	int ASSIGN = 91;
	int DEF_ASSIGN = 92;
	int TRIPLE_EQUAL = 93;
	int EQUAL = 94;
	int NOT_EQUAL = 95;
	int LESS_THAN = 96;
	int LESS_EQUAL = 97;
	int MORE_THAN = 98;
	int MORE_EQUAL = 99;
	int QUESTION = 100;
	int COLON = 101;
	int SEMI = 102;
	int COMMA = 103;
	int DOT = 104;
	int BANG = 105;
	int LSHIFT = 106;
	int RSHIFT = 107;
	int NDVAL = 108;
	int NDVAL2 = 109;
	int NDVAL2SP = 110;
	int SELECT = 111;
	int NDANGELIC = 112;
	int AT = 113;
	int BACKSLASH = 114;
	int LESS_COLON = 115;
	int DOLLAR = 116;
	int DOTASSIGN = 117;
	int DOTPLUS = 118;
	int DOTMINUS = 119;
	int DOTTIMES = 120;
	int DOTDIV = 121;
	int DOTMOD = 122;
	int DOTLT = 123;
	int DOTGT = 124;
	int DOTLTE = 125;
	int DOTGTE = 126;
	int REGEN = 127;
	int CHAR_LITERAL = 128;
	int STRING_LITERAL = 129;
	int ESC = 130;
	int DIGIT = 131;
	int HQUAN = 132;
	int NUMBER = 133;
	int ID = 134;
	int TK_device = 135;
	int TK_library = 136;
	int TK_printfcn = 137;
}
