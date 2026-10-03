package com.takumistudios.liveevents.core.expr;

import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

/**
 * Evaluador aislado de expresiones de las definiciones ("$radius * 0.5", "clamp($count, 1, 80)").
 * <p>
 * Solo admite números, variables {@code $nombre}, + - * / %, paréntesis y una lista cerrada de funciones. Sin bucles,
 * sin acceso a nada externo, con límite de longitud y de profundidad. Se compila una vez y se evalúa al iniciar la
 * instancia, nunca en cada tick.
 */
public final class Expression {
    public static final int MAX_LENGTH = 256;
    public static final int MAX_DEPTH = 32;

    private static final Map<String, DoubleUnaryOperator> UNARY = Map.of(
            "abs", Math::abs, "sqrt", v -> Math.sqrt(Math.max(0, v)), "floor", Math::floor, "ceil", Math::ceil,
            "round", v -> (double) Math.round(v), "sin", Math::sin, "cos", Math::cos);
    private static final Map<String, DoubleBinaryOperator> BINARY = Map.of(
            "min", Math::min, "max", Math::max, "pow", (a, b) -> Math.pow(a, Math.max(-8, Math.min(8, b))));

    private final Node root;
    private final String source;

    private Expression(String source, Node root) {
        this.source = source;
        this.root = root;
    }

    /** Devuelve true si el texto parece una expresión (contiene una variable o un operador). */
    public static boolean looksLikeExpression(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (text.startsWith("$") && text.substring(1).matches("[a-z0-9_]+")) {
            return false; // referencia simple a la paleta o a un parámetro, no una expresión
        }
        return text.contains("$") && text.chars().anyMatch(c -> "+-*/%(".indexOf(c) >= 0);
    }

    private static final int CACHE_SIZE = 512;
    private static final Map<String, Expression> CACHE = new java.util.LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Expression> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    /** Como {@link #compile} pero reutiliza las expresiones ya compiladas (caché LRU acotada). */
    public static Expression compileCached(String text) {
        synchronized (CACHE) {
            Expression cached = CACHE.get(text);
            if (cached != null) {
                return cached;
            }
        }
        Expression compiled = compile(text);
        synchronized (CACHE) {
            CACHE.put(text, compiled);
        }
        return compiled;
    }

    public static Expression compile(String text) {
        if (text == null || text.isBlank()) {
            throw new ExpressionException("expresión vacía");
        }
        if (text.length() > MAX_LENGTH) {
            throw new ExpressionException("expresión demasiado larga (máximo " + MAX_LENGTH + " caracteres)");
        }
        Parser parser = new Parser(text);
        Node node = parser.parseExpression(0);
        parser.skipSpaces();
        if (parser.pos != text.length()) {
            throw new ExpressionException("carácter inesperado '" + text.charAt(parser.pos) + "' en la posición " + parser.pos);
        }
        return new Expression(text, node);
    }

    public double evaluate(Map<String, Double> variables) {
        double result = root.eval(variables);
        if (!Double.isFinite(result)) {
            throw new ExpressionException("el resultado de '" + source + "' no es un número finito");
        }
        return result;
    }

    public String source() {
        return source;
    }

    @FunctionalInterface
    private interface Node {
        double eval(Map<String, Double> vars);
    }

    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        void skipSpaces() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        boolean eat(char c) {
            skipSpaces();
            if (pos < text.length() && text.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        Node parseExpression(int depth) {
            checkDepth(depth);
            Node left = parseTerm(depth + 1);
            while (true) {
                if (eat('+')) {
                    Node l = left, r = parseTerm(depth + 1);
                    left = v -> l.eval(v) + r.eval(v);
                } else if (eat('-')) {
                    Node l = left, r = parseTerm(depth + 1);
                    left = v -> l.eval(v) - r.eval(v);
                } else {
                    return left;
                }
            }
        }

        Node parseTerm(int depth) {
            checkDepth(depth);
            Node left = parseFactor(depth + 1);
            while (true) {
                if (eat('*')) {
                    Node l = left, r = parseFactor(depth + 1);
                    left = v -> l.eval(v) * r.eval(v);
                } else if (eat('/')) {
                    Node l = left, r = parseFactor(depth + 1);
                    left = v -> {
                        double d = r.eval(v);
                        if (d == 0) {
                            throw new ExpressionException("división por cero");
                        }
                        return l.eval(v) / d;
                    };
                } else if (eat('%')) {
                    Node l = left, r = parseFactor(depth + 1);
                    left = v -> {
                        double d = r.eval(v);
                        if (d == 0) {
                            throw new ExpressionException("módulo por cero");
                        }
                        return l.eval(v) % d;
                    };
                } else {
                    return left;
                }
            }
        }

        Node parseFactor(int depth) {
            checkDepth(depth);
            skipSpaces();
            if (eat('-')) {
                Node inner = parseFactor(depth + 1);
                return v -> -inner.eval(v);
            }
            if (eat('+')) {
                return parseFactor(depth + 1);
            }
            if (eat('(')) {
                Node inner = parseExpression(depth + 1);
                if (!eat(')')) {
                    throw new ExpressionException("falta ')' en la posición " + pos);
                }
                return inner;
            }
            if (pos >= text.length()) {
                throw new ExpressionException("la expresión termina de forma inesperada");
            }
            char c = text.charAt(pos);
            if (c == '$') {
                pos++;
                String name = readIdentifier();
                if (name.isEmpty()) {
                    throw new ExpressionException("variable sin nombre en la posición " + pos);
                }
                return v -> {
                    Double value = v.get(name);
                    if (value == null) {
                        throw new ExpressionException("variable desconocida $" + name);
                    }
                    return value;
                };
            }
            if (Character.isDigit(c) || c == '.') {
                int start = pos;
                while (pos < text.length() && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.')) {
                    pos++;
                }
                double value;
                try {
                    value = Double.parseDouble(text.substring(start, pos));
                } catch (NumberFormatException e) {
                    throw new ExpressionException("número no válido en la posición " + start);
                }
                return v -> value;
            }
            if (Character.isLetter(c)) {
                String name = readIdentifier().toLowerCase(Locale.ROOT);
                if (!eat('(')) {
                    throw new ExpressionException("se esperaba '(' después de " + name);
                }
                if (name.equals("clamp") || name.equals("lerp")) {
                    Node a = parseExpression(depth + 1);
                    expectComma();
                    Node b = parseExpression(depth + 1);
                    expectComma();
                    Node t = parseExpression(depth + 1);
                    expectClose();
                    if (name.equals("clamp")) {
                        return v -> Math.max(b.eval(v), Math.min(t.eval(v), a.eval(v)));
                    }
                    return v -> a.eval(v) + (b.eval(v) - a.eval(v)) * t.eval(v);
                }
                DoubleUnaryOperator unary = UNARY.get(name);
                if (unary != null) {
                    Node a = parseExpression(depth + 1);
                    expectClose();
                    return v -> unary.applyAsDouble(a.eval(v));
                }
                DoubleBinaryOperator binary = BINARY.get(name);
                if (binary != null) {
                    Node a = parseExpression(depth + 1);
                    expectComma();
                    Node b = parseExpression(depth + 1);
                    expectClose();
                    return v -> binary.applyAsDouble(a.eval(v), b.eval(v));
                }
                throw new ExpressionException("función no permitida: " + name);
            }
            throw new ExpressionException("carácter inesperado '" + c + "' en la posición " + pos);
        }

        private String readIdentifier() {
            int start = pos;
            while (pos < text.length() && (Character.isLetterOrDigit(text.charAt(pos)) || text.charAt(pos) == '_')) {
                pos++;
            }
            return text.substring(start, pos);
        }

        private void expectComma() {
            if (!eat(',')) {
                throw new ExpressionException("se esperaba ',' en la posición " + pos);
            }
        }

        private void expectClose() {
            if (!eat(')')) {
                throw new ExpressionException("falta ')' en la posición " + pos);
            }
        }

        private void checkDepth(int depth) {
            if (depth > MAX_DEPTH * 3) {
                throw new ExpressionException("expresión demasiado anidada");
            }
        }
    }
}
