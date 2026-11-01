package studio.merami.slimcore.rendering;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MoLang {
    public interface Context {
        double animTime();

        double lifeTime();
    }

    public interface Value {
        double get(Context context);

        boolean constant();
    }

    public record Constant(double value) implements Value {
        @Override
        public double get(Context context) {
            return value;
        }

        @Override
        public boolean constant() {
            return true;
        }
    }

    private record Expression(String source, Node root) implements Value {
        @Override
        public double get(Context context) {
            return root.eval(context);
        }

        @Override
        public boolean constant() {
            return false;
        }
    }

    private interface Node {
        double eval(Context context);
    }

    private final String source;
    private int at;

    private MoLang(String source) {
        this.source = source.toLowerCase(Locale.ROOT);
    }

    public static Value parse(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return new Constant(0);
        try {
            return new Constant(Double.parseDouble(trimmed));
        } catch (NumberFormatException ignored) {
            MoLang parser = new MoLang(trimmed);
            Node root = parser.expression();
            parser.skipSpaces();
            if (parser.at != parser.source.length()) throw parser.error("unexpected '" + parser.source.charAt(parser.at) + "'");
            return new Expression(trimmed, root);
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("MoLang \"" + source + "\": " + message + " at " + at);
    }

    private void skipSpaces() {
        while (at < source.length() && Character.isWhitespace(source.charAt(at))) at++;
    }

    private boolean take(char c) {
        skipSpaces();
        if (at < source.length() && source.charAt(at) == c) {
            at++;
            return true;
        }
        return false;
    }

    private Node expression() {
        Node left = term();
        while (true) {
            if (take('+')) {
                Node a = left;
                Node b = term();
                left = context -> a.eval(context) + b.eval(context);
            } else if (take('-')) {
                Node a = left;
                Node b = term();
                left = context -> a.eval(context) - b.eval(context);
            } else {
                return left;
            }
        }
    }

    private Node term() {
        Node left = unary();
        while (true) {
            if (take('*')) {
                Node a = left;
                Node b = unary();
                left = context -> a.eval(context) * b.eval(context);
            } else if (take('/')) {
                Node a = left;
                Node b = unary();
                left = context -> a.eval(context) / b.eval(context);
            } else {
                return left;
            }
        }
    }

    private Node unary() {
        if (take('-')) {
            Node inner = unary();
            return context -> -inner.eval(context);
        }
        if (take('+')) return unary();
        return primary();
    }

    private Node primary() {
        skipSpaces();
        if (take('(')) {
            Node inner = expression();
            if (!take(')')) throw error("missing ')'");
            return inner;
        }
        if (at >= source.length()) throw error("unexpected end");
        char c = source.charAt(at);
        if (Character.isDigit(c) || c == '.') return number();
        if (Character.isLetter(c) || c == '_') return name();
        throw error("unexpected '" + c + "'");
    }

    private Node number() {
        int start = at;
        while (at < source.length() && (Character.isDigit(source.charAt(at)) || source.charAt(at) == '.')) at++;
        double value;
        try {
            value = Double.parseDouble(source.substring(start, at));
        } catch (NumberFormatException e) {
            throw error("bad number");
        }
        return context -> value;
    }

    private Node name() {
        int start = at;
        while (at < source.length() && (Character.isLetterOrDigit(source.charAt(at)) || source.charAt(at) == '_' || source.charAt(at) == '.')) at++;
        String name = source.substring(start, at);
        List<Node> args = new ArrayList<>();
        if (take('(')) {
            if (!take(')')) {
                do {
                    args.add(expression());
                } while (take(','));
                if (!take(')')) throw error("missing ')'");
            }
        }
        return switch (name) {
            case "query.anim_time", "q.anim_time" -> none(name, args, Context::animTime);
            case "query.life_time", "q.life_time" -> none(name, args, Context::lifeTime);
            case "math.pi" -> none(name, args, context -> Math.PI);
            case "math.sin" -> one(name, args, v -> Math.sin(Math.toRadians(v)));
            case "math.cos" -> one(name, args, v -> Math.cos(Math.toRadians(v)));
            case "math.abs" -> one(name, args, Math::abs);
            case "math.sqrt" -> one(name, args, Math::sqrt);
            case "math.floor" -> one(name, args, Math::floor);
            case "math.ceil" -> one(name, args, Math::ceil);
            case "math.round" -> one(name, args, v -> (double) Math.round(v));
            case "math.min" -> two(name, args, Math::min);
            case "math.max" -> two(name, args, Math::max);
            case "math.mod" -> two(name, args, (a, b) -> a % b);
            case "math.pow" -> two(name, args, Math::pow);
            case "math.clamp" -> {
                arity(name, args, 3);
                Node value = args.get(0);
                Node min = args.get(1);
                Node max = args.get(2);
                yield context -> Math.max(min.eval(context), Math.min(max.eval(context), value.eval(context)));
            }
            case "math.lerp" -> {
                arity(name, args, 3);
                Node a = args.get(0);
                Node b = args.get(1);
                Node t = args.get(2);
                yield context -> a.eval(context) + (b.eval(context) - a.eval(context)) * t.eval(context);
            }
            default -> throw error("unknown name " + name);
        };
    }

    private void arity(String name, List<Node> args, int count) {
        if (args.size() != count) throw error(name + " takes " + count + " argument(s), got " + args.size());
    }

    private Node none(String name, List<Node> args, Node node) {
        arity(name, args, 0);
        return node;
    }

    private Node one(String name, List<Node> args, java.util.function.DoubleUnaryOperator function) {
        arity(name, args, 1);
        Node value = args.get(0);
        return context -> function.applyAsDouble(value.eval(context));
    }

    private Node two(String name, List<Node> args, java.util.function.DoubleBinaryOperator function) {
        arity(name, args, 2);
        Node a = args.get(0);
        Node b = args.get(1);
        return context -> function.applyAsDouble(a.eval(context), b.eval(context));
    }
}
