package ParserPackage;

import jdk.nashorn.api.scripting.ScriptObjectMirror;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.regex.Pattern;

public class Parser {
    public static ScriptEngine engine;

    static {
        engine = new ScriptEngineManager().getEngineByName("js");
    }

    static String getFileExtension(File file) {
        String extension = "";

        try {
            if (file != null && file.exists()) {
                String name = file.getName();
                int index = name.lastIndexOf(".");
                if (index != -1)
                    extension = name.substring(index + 1);
                else extension = "";
            }
        } catch (Exception e) {
            extension = "";
        }

        return extension;
    }

    public static Collection<Value> compile(String filename) throws Exception {
        return compileCode(getCodeFromFile(filename));
    }

    private static String getCodeFromFile(String filename) throws Exception {
        File file = new File(filename);
        if (!getFileExtension(file).equalsIgnoreCase("psl")) {
            if (getFileExtension(file).equals("")) {
                file = new File(filename + ".psl");
                if (!getFileExtension(file).equalsIgnoreCase("psl")) throw new Exception("Wrong file: '" + filename + "'");
            } else throw new Exception("Wrong file extension: " + file.getAbsolutePath());
        }
        FileInputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        fis.read(data);
        fis.close();
        return new String(data, StandardCharsets.UTF_8);
    }

    private static Collection<Value> compileCode(String code) {

    }

    public static Value parse(String filename, Environment env) throws Exception {
        File file = new File(filename);
        if (!getFileExtension(file).equalsIgnoreCase("psl")) {
            if (getFileExtension(file).equals("")) {
                file = new File(filename + ".psl");
                if (!getFileExtension(file).equalsIgnoreCase("psl")) throw new Exception("Wrong file: '" + filename + "'");
            } else throw new Exception("Wrong file extension: " + file.getAbsolutePath());
        }
        FileInputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        fis.read(data);
        fis.close();
        String code = new String(data, StandardCharsets.UTF_8);
        return parseCode(code, env, file);
    }

    static Value parseCode(String code, Environment env, File file) throws Exception {
        Value exports = Value.NULL;
        final Environment[] environment = new Environment[1];
        if (env == null) {
            Environment defaultEnvironment = new Environment(
                    new Collection<>(
                            new Variable("print",
                                    new Value(
                                            new PSLFunction(arguments -> {
                                                Collection<Value> args = arguments.getArgs();
                                                System.out.print(args.join());
                                                return null;
                                            })
                                    )
                            ),
                            new Variable("random",
                                    new Value(
                                            new PSLFunction(arguments -> {
                                                Collection<Value> args = arguments.getArgs();
                                                switch (args.size()) {
                                                    case 0:
                                                        return new Value(Math.random());
                                                    case 1:
                                                        double arg = ((Number)args.get(0).getValue()).doubleValue();
                                                        return new Value(Math.random() * arg);
                                                    default:
                                                        double first = ((Number)args.get(0).getValue()).doubleValue();
                                                        double second = ((Number)args.get(1).getValue()).doubleValue();
                                                        return new Value(first + Math.random() * (second - first));
                                                }
                                            })
                                    )
                            ),
                            new Variable("export",
                                    new Value(
                                            new PSLFunction(arguments -> {
                                                Collection<Value> args = arguments.getArgs();
                                                assert args.size() == 1;
                                                exports.setValue(args.get(0).getValue());
                                                return args.get(0);
                                            })
                                    )
                            ),
                            new Variable("require",
                                    new Value(
                                            new PSLFunction(
                                                    arguments -> {
                                                        Collection<Value> args = arguments.getArgs();
                                                        assert args.size() == 1;
                                                        try {
                                                            return parse(args.get(0).getValue().toString(), environment[0]);
                                                        } catch (Exception e) {
                                                            e.printStackTrace();
                                                            return null;
                                                        }
                                                    }
                                            )

                                    )
                            ),
                            new Variable("set",
                                    new Value(
                                            new PSLFunction(
                                                    arguments -> {
                                                        Collection<Value> args = arguments.getArgs();
                                                        assert args.size() == 2;
                                                        String name = (String) args.get(0).getValue();
                                                        Value value = args.get(1);
                                                        Variable variable = environment[0].getVariables().findFirst(variable1 -> variable1.getName().equals(name));
                                                        if (variable == null) {
                                                            environment[0].addVariable(new Variable(name, value));
                                                        } else {
                                                            variable.setValue(value);
                                                        }
                                                        return value;
                                                    }
                                            )

                                    )
                            )
                    ),
                    new Collection<>(
                            new BinaryOperator(
                                "=",
                                (a, b) -> {
                                    if (a.getType() == Identifier.class) {
                                        String name = ((Identifier) a.getValue()).getName();
                                        Variable variable = environment[0].getVariables().findFirst(variable1 -> variable1.getName().equals(name));
                                        if (variable == null) {
                                            environment[0].addVariable(new Variable(name, b));
                                        } else {
                                            variable.setValue(b);
                                        }
                                        return b;
                                    } else {
                                        return null;
                                    }
                                },
                                0
                            )
                    )
            );
            environment[0] = defaultEnvironment;
        } else environment[0] = env;
        Parser.engine.put("get", (Function<String, Object>) name -> {
            Variable variable = environment[0].getVariables().findFirst(var -> var.getName().equals(name));
            if (variable == null) return null;
            return variable.getValue().getValue();
        });
        Parser.engine.put("evalPSL", (Function<String, Value>) code1 -> {
            try {
                return Parser.parseCode(code1, environment[0], null);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
        Collection<Rule> rules = new Collection<Rule>(){{
            add(new Rule(";", "SEMICOLON"));
            add(new Rule(",", "COMMA"));
            add(new Rule("//.*", "LINE_COMMENT"));
            add(new Rule("\\(", "LEFT_PAREN"));
            add(new Rule("\\)", "RIGHT_PAREN"));
            add(new Rule("(\\d*(\\.\\d+)(e-?\\d+)?)|(\\d+(\\.\\d+)?(e-?\\d+)?)", "VALUE_NUMBER"));
            add(new Rule("VALUE_STRING",
                    new Collection<>(
                            "'([^'\\\\\\n]|(\\\\.))*'",
                            "\"([^\"\\\\\\n]|(\\\\.))*\""
                    )
            ));
            add(new Rule("\\`([^\\`]|(\\\\.))*\\`", "VALUE_JS"));
            add(new Rule("[a-zA-Z_$][a-zA-Z0-9_$]*", "IDENTIFIER"));
            add(new Rule("\\r?\\n", "NEWLINE"));
        }};
        Rule operatorRule = new Rule(new Collection<>(), "OPERATOR");
        for (BinaryOperator operator: environment[0].getBinaryOperators()) {
            operatorRule.addPattern(Pattern.compile(operator.getRegex()));
        }
        rules.add(operatorRule);
        Rule toSkip = new Rule("[^\\S\\n]+");
        TokenHolder tokenHolder;
        try {
            tokenHolder = Lexer.lex(code, rules, toSkip);
        } catch (LexingException e) {
            String[] chunks = code.substring(0, e.getPosition() + 1).split("\\r?\\n");
            int relativePosition = chunks[chunks.length - 1].length() - 1;
            int k = chunks.length - 1;
            StringBuilder spaces = new StringBuilder();
            for (int i = 0; i < relativePosition; i++) spaces.append(" ");
            throw new Exception("Unexpected token in " + (file == null ? "<eval>" : file.getAbsolutePath()) + ":" + (k + 1) + ":" + relativePosition + " (absolute position: " + e.getPosition() + "):\n" +
                    code.split("\\r?\\n")[k] + "\n" +
                    spaces.toString() + "^");
        }

        Collection<Value> output = new Collection<>();
        Collection<Value> operator = new Collection<>();

        for(Token token: tokenHolder.getTokens()) {
            if (token.getName().startsWith("VALUE_")) {
                if (token.getName().equals("VALUE_STRING")) {
                    output.add(parseString(token.getValue()));
                } else if (token.getName().equals("VALUE_NUMBER")) {
                    output.add(parseNumber(token.getValue()));
                } else if (token.getName().equals("VALUE_JS")) {
                    Value value = parseJSValue(token.getValue());
                    if (!(value instanceof JSValue)) output.add(new Value(new DenyCall()));
                    output.add(value);
                } else {
                    /* wtf */
                }
            } else if (token.getName().equals("LEFT_PAREN")) {
                operator.add(new Value(new ApproveCall()));
                if ((Function.class.isAssignableFrom(output.get(output.size() - 1).getType())) || (output.get(output.size() - 1).getType() == Identifier.class)) {
                    Value f = output.remove(output.size() - 1);
                    output.remove(output.size() - 1);
                    operator.add(f);
                    output.add(new Value(new ApproveCall()));
                }
            } else if (token.getName().equals("RIGHT_PAREN")) {
                while (operator.get(operator.size() - 1).getType() != ApproveCall.class) {
                    output.add(operator.remove(operator.size() - 1));
                }
                if (operator.get(operator.size() - 1).getType() == ApproveCall.class) {
                    operator.remove(operator.size() - 1);
                }
            } else if (token.getName().equals("IDENTIFIER")) {
                output.add(new Value(new DenyCall()));
                output.add(new Value(new Identifier(token.getValue())));
            } else if (token.getName().equals("OPERATOR")) {
                BinaryOperator binaryOperator = environment[0].getBinaryOperators().findFirst(binaryOperator1 -> binaryOperator1.getName().equals(token.getValue()));
                Value lastOperator = operator.last();
                if (lastOperator != null) while (
                        (
                                ((operator.last().getType() == BinaryOperator.class) && (((BinaryOperator)operator.last().getValue()).getPrecedence() >= binaryOperator.getPrecedence())) ||
                                (((operator.last().getType() == Identifier.class) || Function.class.isAssignableFrom(operator.last().getType())) && (operator.get(operator.size() - 2).getType() != DenyCall.class))
                        ) && (operator.last().getType() != ApproveCall.class)
                ) {
                    output.add(operator.pop());
                }
                operator.add(new Value(binaryOperator));
            } else if (token.getName().equals("SEMICOLON")) {
                while (operator.size() > 0) {
                    output.add(operator.pop());
                }
            }
        }
        while (operator.size() > 0) {
            output.add(operator.pop());
        }

        Collection<Value> stack = new Collection<>();
        for (Value value : output) {
            if (Function.class.isAssignableFrom(value.getType())) {
                Collection<Value> args = new Collection<>();
                Value val;
                while (!ArgumentEnd.class.isAssignableFrom((val = stack.remove(stack.size() - 1)).getType())) {
                    args.add(val);
                }
                if (val.getType() == ApproveCall.class) {
                    Function<PSLFunctionArguments, Value> function = (Function<PSLFunctionArguments, Value>) value.getValue();
                    PSLFunctionArguments arguments = new PSLFunctionArguments(environment[0], args);
                    stack.add(function.apply(arguments));
                } else stack.add(value);
            } else if (value.getType() == Identifier.class) {
                Variable variable = environment[0].getVariables().findFirst(var -> var.getName().equals(((Identifier)value.getValue()).getName()));
                if (variable == null) {
                    stack.pop();
                    stack.add(new Value((Identifier) value.getValue()));
                    continue;
                }
                if (Function.class.isAssignableFrom(variable.getValue().getType())) {
                    Collection<Value> args = new Collection<>();
                    Value val;
                    while (!ArgumentEnd.class.isAssignableFrom((val = stack.remove(stack.size() - 1)).getType())) {
                        args.add(val);
                    }
                    if (val.getType() == ApproveCall.class) {
                        stack.add(((Function<PSLFunctionArguments, Value>) variable.getValue().getValue()).apply(new PSLFunctionArguments(environment[0], args)));
                    } else stack.add(variable.getValue());
                } else {
                    Collection<Value> args = new Collection<>();
                    Value val;
                    while (!ArgumentEnd.class.isAssignableFrom((val = stack.remove(stack.size() - 1)).getType())) {
                        args.add(val);
                    }
                    if (val.getType() == ApproveCall.class) {
                        throw new Exception("'" + variable.getName() + "' is not a function");
                    } else stack.add(variable.getValue());
                }
            } else if (value.getType() == BinaryOperator.class) {
                Value operand2 = stack.pop();
                Value operand1 = stack.pop();
                stack.add(((BinaryOperator) value.getValue()).eval(operand1, operand2));
            } else stack.add(value);
        }

        return exports;
    }

    public static Value parse(String filename) throws Exception {
        return parse(filename, null);
    }
    private static Value parseString(String s) {
        return new Value(s.substring(1, s.length() - 1).replaceAll("(\\\\)(.)", "$2"));
    }
    private static Value parseNumber(String s) {
        String[] parts = s.split("e");
        double number = Double.parseDouble(parts[0]);
        try {
            long exponent = Long.parseLong(parts[1]);
            number *= Math.pow(10, exponent);
        } catch (IndexOutOfBoundsException ignored){}
        return new Value(number);
    }
    private static Value parseFunction(String s) {
        return new Value(new JSFunction(s));
    }
    private static Value parseJSValue(String s) {
        Object result = null;
        try {
            result = engine.eval(s.substring(1, s.length() - 1));
        } catch (ScriptException e) {
            e.printStackTrace();
        }
        try {
            if (Function.class.isAssignableFrom(result.getClass()) || ((ScriptObjectMirror) result).isFunction()) {
                return parseFunction(s);
            } else return new JSValue(s);
        } catch (ClassCastException ignored) {
            return new JSValue(s);
        }
    }
}

class JSFunction implements Function<Object, Value> {
    private String s;

    public JSFunction(String s) {
        this.s = s;
    }

    @Override
    public Value apply(Object values) {
        Object[] args = new Object[]{values};
        try {
            args = ((PSLFunctionArguments) values).getArgs().map(value -> value.getType().cast(value.getValue())).reverse().toArray();
        } catch (ClassCastException ignored){}
        Object result = null;
        try {
            result = Parser.engine.eval(s.substring(1, s.length() - 1));
        } catch (ScriptException e) {
            e.printStackTrace();
        }
        Object returnValue;
        try {
            returnValue = ((ScriptObjectMirror) result).call(null, args);
        } catch (ClassCastException e) {
            try {
                returnValue = ((Function<PSLFunctionArguments, Value>) result).apply((PSLFunctionArguments) values);
            } catch (ClassCastException e1) {
                returnValue = ((Function<PSLFunctionArguments, Value>) result).apply(new PSLFunctionArguments(null, new Collection<>(new Value(values))));
            }
        }
        return (returnValue == null ? Value.NULL : new Value(returnValue));
    }
}

class PSLFunction implements Function<Object, Value> {
    private Function<PSLFunctionArguments, Value> action;

    public Value executeAction(PSLFunctionArguments arguments) {
        return action.apply(arguments);
    }

    public PSLFunction(Function<PSLFunctionArguments, Value> action) {
        this.action = action;
    }

    public PSLFunction() {}

    @Override
    public Value apply(Object o) {
        if (o instanceof PSLFunctionArguments) {
            PSLFunctionArguments arguments = (PSLFunctionArguments) o;
            arguments.setArgs(arguments.getArgs().reverse());
            Value value = executeAction(arguments);
            return (value == null ? Value.NULL : value);
        } else return executeAction(new PSLFunctionArguments(null, new Collection<>(new Value(o))));
    }
}

class JSValue extends Value {
    public JSValue(Object object) {
        if (object.getClass() == String.class) {
            Object result = null;
            String s = object.toString();
            try {
                result = Parser.engine.eval(s.substring(1, s.length() - 1));
            } catch (ScriptException e) {
                e.printStackTrace();
            }
            setValue(result);
        } else {
            setValue(object);
        }
    }
}

class Identifier {
    private String name;

    public Identifier(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}

class PSLFunctionArguments {
    private Environment environment;
    private Collection<Value> args;

    public PSLFunctionArguments(Environment environment, Collection<Value> args) {
        this.environment = environment;
        this.args = args;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    public Collection<Value> getArgs() {
        return args;
    }

    public void setArgs(Collection<Value> args) {
        this.args = args;
    }
}

class ArgumentEnd {}
class ApproveCall extends ArgumentEnd {}
class DenyCall extends ArgumentEnd {}