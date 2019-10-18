// based on the grammars for 
//    SQLite by Bart Kiers (https://github.com/antlr/grammars-v4/blob/master/sqlite/SQLite.g4), and
//    Java8 by Terence Parr & Sam Harwell (https://github.com/antlr/grammars-v4/blob/master/java8/Java8.g4)

grammar Xbel;

statement 
    : (variable '=')? valueCreation
    ; 

variable
    : variableDefinition
    | variableName
    ;

variableDefinition
    : solType variableName
    ;

variableName
    : Identifier
    | Identifier ':' Identifier
    | Identifier '.' Identifier
    ;

valueCreation
    : variableName
    | methodCall
    | literal
    ;

methodCall
    : methodName=Identifier '(' (methodParameter (',' methodParameter)* )? ')'
    ;

methodParameter
    : variableName
    | literal
    ;

// Literals

literalRule : literal EOF;

literal 
    : STRING_LITERAL
    | arrayValue
    | BOOLEAN_LITERAL
    | BYTE_AND_ADDRESS_LITERAL
    | FIXED_LITERAL
    | INT_LITERAL
    ;

arrayValue
    : stringArrayValue
    | intArrayValue
    | booleanArrayValue
    | fixedArrayValue
    | byteAndAddressArrayValue
    ;

stringArrayValue
    : '{' (STRING_LITERAL (',' STRING_LITERAL)*)? '}'
    ;

intArrayValue
    : '{' ((INT_LITERAL) (',' INT_LITERAL)*)? '}'
    ;

fixedArrayValue
    : '{' fixedArrayElement (',' fixedArrayElement)* '}'
    ;

fixedArrayElement
    : FIXED_LITERAL
    | INT_LITERAL
    ;

booleanArrayValue
    : '{' (BOOLEAN_LITERAL (',' BOOLEAN_LITERAL)*)? '}'
    ;

byteAndAddressArrayValue
    : '{' (BYTE_AND_ADDRESS_LITERAL (',' BYTE_AND_ADDRESS_LITERAL)*)? '}'
    ;

STRING_LITERAL : '"' ('\\"' | ~["\r\n])* '"';

FIXED_LITERAL : [0-9]* '.' [0-9]+ ;

INT_LITERAL : [0-9]+;

BOOLEAN_LITERAL 
  : T R U E
  | F A L S E
  ;

BYTE_AND_ADDRESS_LITERAL : '0x' [0-9a-fA-F]+;

// TYPES

solTypeRule : solType EOF;

solType 
    :
    | SOL_ADDRESS_TYPE
    | SOL_ADDRESS_ARRAY_TYPE
    | SOL_BOOL_ARRAY_TYPE
    | SOL_BOOL_TYPE
    | SOL_BYTE_ARRAY_TYPE
    | SOL_BYTE_TYPE
    | SOL_FIXED_ARRAY_TYPE
    | SOL_FIXED_TYPE
    | SOL_INT_ARRAY_TYPE
    | SOL_INT_TYPE
    | SOL_STRING_TYPE
    ;

SOL_FIXED_ARRAY_TYPE
    : SOL_FIXED_TYPE '[' ']'
    ;

SOL_FIXED_TYPE
    : (SOL_UNSIGNED)? 'fixed'(SOL_NUMBER_LENGTH'x'SOL_FIXED_N)?
    ;

SOL_BYTE_ARRAY_TYPE
    : SOL_BYTE_TYPE '[' ']'
    ;

SOL_BYTE_TYPE 
    : 'byte' ('s' SOL_BYTES_LENGTH)?
    | 'bytes'
    ;

SOL_INT_ARRAY_TYPE
    : SOL_INT_TYPE '[' ']'
    ;

SOL_INT_TYPE
    : (SOL_UNSIGNED)? 'int' SOL_NUMBER_LENGTH?
    ;

SOL_ADDRESS_ARRAY_TYPE 
    : SOL_ADDRESS_TYPE '[' ']'
    ;

SOL_ADDRESS_TYPE
    : 'address'
    ;

SOL_BOOL_ARRAY_TYPE
    : SOL_BOOL_TYPE '[' ']'
    ;

SOL_BOOL_TYPE
    : 'bool'
    ; 

SOL_BYTES_LENGTH
    : [1-9]|[1-2][0-9]|[3][0-2]
    ;

SOL_UNSIGNED 
    : 'u'
    ;

SOL_NUMBER_LENGTH
    : '8'|'16'|'24'|'32'|'40'|'48'|'56'|'64'|'72'|'80'|'88'|'96'|'104'|'112'|'120'|'128'|'136'|'144'|'152'|'160'|'168'|'176'|'184'|'192'|'200'|'208'|'216'|'224'|'232'|'240'|'248'|'256'
    ;

SOL_FIXED_N
    : [1-7]?[0-9]|[8][0-1]
    ;

SOL_STRING_TYPE
    : 'string'
    ;


// FRAGMENTS

fragment A : [aA];
fragment B : [bB];
fragment C : [cC];
fragment D : [dD];
fragment E : [eE];
fragment F : [fF];
fragment G : [gG];
fragment H : [hH];
fragment I : [iI];
fragment J : [jJ];
fragment K : [kK];
fragment L : [lL];
fragment M : [mM];
fragment N : [nN];
fragment O : [oO];
fragment P : [pP];
fragment Q : [qQ];
fragment R : [rR];
fragment S : [sS];
fragment T : [tT];
fragment U : [uU];
fragment V : [vV];
fragment W : [wW];
fragment X : [xX];
fragment Y : [yY];
fragment Z : [zZ];


// Identifier


Identifier
	:	Letter LetterOrDigit*
    | '_' LetterOrDigit+
	;

fragment Letter
	:	[a-zA-Z] // these are the "java letters" below 0x7F
	|	// covers all characters above 0x7F which are not a surrogate
		~[\u0000-\u007F\uD800-\uDBFF]
		{Character.isJavaIdentifierStart(_input.LA(-1))}?
	|	// covers UTF-16 surrogate pairs encodings for U+10000 to U+10FFFF
		[\uD800-\uDBFF] [\uDC00-\uDFFF]
		{Character.isJavaIdentifierStart(Character.toCodePoint((char)_input.LA(-2), (char)_input.LA(-1)))}?
	;

fragment LetterOrDigit
	:	[a-zA-Z0-9$_] // these are the "java letters or digits" below 0x7F
	|	// covers all characters above 0x7F which are not a surrogate
		~[\u0000-\u007F\uD800-\uDBFF]
		{Character.isJavaIdentifierPart(_input.LA(-1))}?
	|	// covers UTF-16 surrogate pairs encodings for U+10000 to U+10FFFF
		[\uD800-\uDBFF] [\uDC00-\uDFFF]
		{Character.isJavaIdentifierPart(Character.toCodePoint((char)_input.LA(-2), (char)_input.LA(-1)))}?
	;

WS : [ \u000B\t\r\n]+ -> skip;

COMMENT
    :   '/*' .*? '*/' -> skip
    ;

LINE_COMMENT
    :   '//' ~[\r\n]* -> skip
    ;