// $ANTLR : "StreamItParserFE.g" -> "StreamItParserFE.java"$

	package streamit.frontend;

	import streamit.frontend.nodes.*;

	import java.util.Collections;
	import java.io.DataInputStream;
	import java.util.List;

	import java.util.ArrayList;

public interface StreamItParserFETokenTypes {
	int EOF = 1;
	int NULL_TREE_LOOKAHEAD = 3;
	int TK_filter = 4;
	int TK_pipeline = 5;
	int TK_splitjoin = 6;
	int TK_sbox = 7;
	int TK_feedbackloop = 8;
	int TK_portal = 9;
	int TK_to = 10;
	int TK_handler = 11;
	int TK_add = 12;
	int TK_split = 13;
	int TK_join = 14;
	int TK_duplicate = 15;
	int TK_roundrobin = 16;
	int TK_body = 17;
	int TK_loop = 18;
	int TK_enqueue = 19;
	int TK_init = 20;
	int TK_prework = 21;
	int TK_work = 22;
	int TK_phase = 23;
	int TK_peek = 24;
	int TK_pop = 25;
	int TK_push = 26;
	int TK_boolean = 27;
	int TK_float = 28;
	int TK_bit = 29;
	int TK_int = 30;
	int TK_void = 31;
	int TK_double = 32;
	int TK_complex = 33;
	int TK_struct = 34;
	int TK_template = 35;
	int TK_if = 36;
	int TK_else = 37;
	int TK_while = 38;
	int TK_for = 39;
	int TK_switch = 40;
	int TK_case = 41;
	int TK_default = 42;
	int TK_break = 43;
	int TK_continue = 44;
	int TK_return = 45;
	int TK_pi = 46;
	int TK_true = 47;
	int TK_false = 48;
	int TK_implements = 49;
	int TK_overrides = 50;
	int ARROW = 51;
	int WS = 52;
	int SL_COMMENT = 53;
	int ML_COMMENT = 54;
	int LPAREN = 55;
	int RPAREN = 56;
	int LCURLY = 57;
	int RCURLY = 58;
	int LSQUARE = 59;
	int RSQUARE = 60;
	int PLUS = 61;
	int PLUS_EQUALS = 62;
	int INCREMENT = 63;
	int MINUS = 64;
	int MINUS_EQUALS = 65;
	int DECREMENT = 66;
	int STAR = 67;
	int STAR_EQUALS = 68;
	int DIV = 69;
	int DIV_EQUALS = 70;
	int MOD = 71;
	int LOGIC_AND = 72;
	int LOGIC_OR = 73;
	int BITWISE_AND = 74;
	int BITWISE_OR = 75;
	int BITWISE_XOR = 76;
	int ASSIGN = 77;
	int EQUAL = 78;
	int NOT_EQUAL = 79;
	int LESS_THAN = 80;
	int LESS_EQUAL = 81;
	int MORE_THAN = 82;
	int MORE_EQUAL = 83;
	int QUESTION = 84;
	int COLON = 85;
	int SEMI = 86;
	int COMMA = 87;
	int DOT = 88;
	int BANG = 89;
	int LSHIFT = 90;
	int RSHIFT = 91;
	int NDVAL = 92;
	int NDVAL2 = 93;
	int SELECT = 94;
	int CHAR_LITERAL = 95;
	int STRING_LITERAL = 96;
	int ESC = 97;
	int DIGIT = 98;
	int HQUAN = 99;
	int NUMBER = 100;
	int ID = 101;
	int TK_do = 102;
}
