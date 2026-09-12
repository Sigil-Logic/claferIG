/*
 * Copyright (C) 2012 Jimmy Liang <http://gsd.uwaterloo.ca>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.clafer.ig;

import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4.ErrorSyntax;
import edu.mit.csail.sdg.alloy4.ErrorWarning;
import edu.mit.csail.sdg.alloy4.Pos;
import edu.mit.csail.sdg.alloy4.SafeList;
import edu.mit.csail.sdg.ast.Command;
import edu.mit.csail.sdg.ast.CommandScope;
import edu.mit.csail.sdg.ast.Func;
import edu.mit.csail.sdg.ast.Sig;
import edu.mit.csail.sdg.parser.CompModule;
import edu.mit.csail.sdg.parser.CompUtil;
import edu.mit.csail.sdg.translator.A4Options;
import edu.mit.csail.sdg.translator.A4Solution;
import edu.mit.csail.sdg.translator.TranslateAlloyToKodkod;
import kodkod.engine.satlab.SATFactory;
import java.io.EOFException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Set;
import static org.clafer.ig.Util.*;

public final class AlloyIG {

    // The only to detect unsatisfiability to is wait for the minimized callback to be called.
    // If it is called, then unsatisfiable. Otherwise not. Very strange way of doing things
    // but that's how its done in Alloy as far as I can tell.
    // See alloy4whole/SimpleReporter.java for details.
    private static class AlloyIGReporter extends A4Reporter {

        private int minimizedBefore;
        private int minimizedAfter;

        @Override
        public void warning(ErrorWarning msg) {
            System.err.print("Relevance Warning:\n" + (msg.toString().trim()) + "\n\n");
            System.err.flush();
        }

        @Override
        public void minimized(Object command, int before, int after) {
            minimizedBefore = before;
            minimizedAfter = after;
            super.minimized(command, before, after);
        }
    };

    private static interface Operation {
    }

    private static class LoadOperation implements Operation {

        private final String model;

        public LoadOperation(String model) {
            this.model = model;
        }

        public String getModel() {
            return model;
        }
    }

    private static class ResolveOperation implements Operation {
    }

    private static class NextOperation implements Operation {
    }

    private static class SaveStateOperation implements Operation {
        // To generate counterexamples, we mutate the model. Save
        // the state first and restore the state once completed so
        // the model returns to its original form.
    }

    private static class RestoreStateOperation implements Operation {
    }

    private static class RemoveConstraintOperation implements Operation {

        private final int y, x, y2, x2;

        public RemoveConstraintOperation(int y, int x, int y2, int x2) {
            this.y = y;
            this.x = x;
            this.y2 = y2;
            this.x2 = x2;
        }

        public Pos getPos(String filename) {
            // Pos.equals compares filenames.  Alloy 6's
            // parseEverything_fromString parses via a temporary file, so AST
            // positions carry that file's name; a constraint position must
            // carry the same name to match (the 4.2-era in-memory parse used
            // "" here).
            return new Pos(filename, x, y, x2, y2);
        }
    }

    private static class UnsatCoreOperation implements Operation {
    }

    private static class QuitOperation implements Operation {
    }

    private static class SetGlobalScopeOperation implements Operation {

        private final int scopeSize;

        public SetGlobalScopeOperation(int scopeSize) {
            this.scopeSize = scopeSize;
        }

        public int getScopeSize() {
            return scopeSize;
        }
    }

    private static class SetScopeOperation implements Operation {

        private final String sig;
        private final int scopeSize;

        public SetScopeOperation(String sig, int scopeSize) {
            if (sig == null) {
                throw new NullPointerException();
            }
            this.sig = sig;
            this.scopeSize = scopeSize;
        }

        public String getSig() {
            return sig;
        }

        public int getScopeSize() {
            return scopeSize;
        }
    }

    private static class SetUnsatCoreMinimizationOperation implements Operation {

        private final int minimizationLevel;

        public SetUnsatCoreMinimizationOperation(int minimizationLevel) {
            if (minimizationLevel < 0 || minimizationLevel > 2) {
                throw new IllegalArgumentException(minimizationLevel + " is in invalid optimization level.");
            }
            this.minimizationLevel = minimizationLevel;
        }

        public int getMinimizationLevel() {
            return minimizationLevel;
        }
    }

    private static class SetBitwidthOperation implements Operation {
        private final int bitwidth;

        public SetBitwidthOperation(int bitwidth) {
            if(bitwidth < 1) {
                throw new IllegalArgumentException(bitwidth + " is an invalid bitwidth.");
            }
            this.bitwidth = bitwidth;
        }

        public int getBitwidth() {
            return bitwidth;
        }
    }

    private static Operation nextOperation() throws IOException {
        String op = readMessage();
        if (op == null || op.equals("quit")) {
            return new QuitOperation();
        } else if (op.equals("load")) {
            String model = readMessage();
            return new LoadOperation(model);
        } else if (op.equals("next")) {
            return new NextOperation();
        } else if (op.equals("setGlobalScope")) {
            int scopeSize = readIntMessage();
            return new SetGlobalScopeOperation(scopeSize);
        } else if (op.equals("setScope")) {
            String sig = readMessage();
            int scopeSize = readIntMessage();
            return new SetScopeOperation(sig, scopeSize);
        } else if (op.equals("resolve")) {
            return new ResolveOperation();
        } else if (op.equals("saveState")) {
            return new SaveStateOperation();
        } else if (op.equals("restoreState")) {
            return new RestoreStateOperation();
        } else if (op.equals("removeConstraint")) {
            // The second column is one less than it should be.
            // Not sure if Alloy bug or intended.
            return new RemoveConstraintOperation(readIntMessage(), readIntMessage(), readIntMessage(), readIntMessage() - 1);
        } else if (op.equals("unsatCore")) {
            return new UnsatCoreOperation();
        } else if (op.equals("unsatCoreMinimization")) {
            int optimizationLEvel = readIntMessage();
            return new SetUnsatCoreMinimizationOperation(optimizationLEvel);
        } else if (op.equals("setBitwidth")) {
            int bitwidth = readIntMessage();
            return new SetBitwidthOperation(bitwidth);
        }
        throw new AlloyIGException("Unknown op " + op);
    }

    private static Command setGlobalScope(int scopeSize, Command c) {
        return new Command(c.pos, c.nameExpr, c.label, c.check, scopeSize, c.bitwidth, c.maxseq,
                c.minprefix, c.maxprefix, c.expects, c.scope, c.additionalExactScopes,
                c.commandKeyword, c.formula, c.parent);
    }

    private static Command setBitwidth(int bitwidth, Command c) {
        return new Command(c.pos, c.nameExpr, c.label, c.check, c.overall, bitwidth, c.maxseq,
                c.minprefix, c.maxprefix, c.expects, c.scope, c.additionalExactScopes,
                c.commandKeyword, c.formula, c.parent);
    }

    private static Command setScopeSize(Sig sig, int scopeSize, Command c) throws ErrorSyntax {
        CommandScope existing = c.getScope(sig);
        if (existing == null) {
            return c.change(sig, false, scopeSize);
        }
        return c.change(sig, existing.isExact, scopeSize, scopeSize, existing.increment);
    }

    private static String toXml(A4Solution ans) throws Err, IOException {
        StringWriter xml = new StringWriter();
        // Alloy 6's A4SolutionWriter no longer accepts a null macros iterable.
        ans.writeXML(new PrintWriter(xml), new ArrayList<Func>(0), null);
        return xml.toString();
    }

    private static String multiplicity(Sig sig) {
        if (sig.isOne != null) {
            return "One";
        } else if (sig.isLone != null) {
            return "Lone";
        } else if (sig.isSome != null) {
            return "Some";
        }
        return "Any";
    }

    public static void main(String[] args) throws IOException, Err {
        // Quiet the slf4j-simple binding bundled with Alloy: kodkod logs INFO
        // progress to stderr, which would leak into claferIG's terminal.
        // (Both property spellings, for old and new slf4j-simple versions.)
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");
        System.setProperty("org.slf4j.simplelogger.defaultlog", "warn");
        // With proof logging, prover peers are released by the finalizer (the
        // proof outlives the solve); NativeSolver logs each such release at
        // WARN, which would spam claferIG's console on every unsat core.
        System.setProperty("org.slf4j.simpleLogger.log.kodkod.engine.satlab.NativeSolver", "error");
        try {
            run(args);
        } catch (EOFException e) {
            System.err.println("AlloyIG stream closed.");
        }
    }

    private static String removeCurly(String string) {
        int length = string.length();
        if (length > 0 && string.charAt(0) == '{' && string.charAt(length - 1) == '}') {
            return string.substring(1, length - 1);
        }
        throw new IllegalArgumentException("\"" + string + "\" is not surrounded by curly brackets.");
    }

    public static void run(String[] args) throws IOException, Err {
        AlloyIGReporter rep = new AlloyIGReporter();

        CompModule world = null;
        SafeList<Sig> sigs = null;
        Command command = null;
        String modelFilename = "";

        A4Solution ans = null;
        Operation operation = null;

        A4Options options = new A4Options();
        // Use fastest
        options.coreMinimization = 2;
        SATFactory miniSatProver = SATFactory.find("minisat.prover")
                .filter(SATFactory::isPresent).orElse(null);
        if (miniSatProver != null) {
            options.solver = miniSatProver;
        } else {
            // Without the prover the minimized() callback never fires, so
            // unsatCore degrades gracefully to an empty core.
            System.err.println("AlloyIG: native MiniSat prover unavailable on this platform;"
                    + " falling back to SAT4J (UNSAT cores unavailable).");
            options.solver = SATFactory.DEFAULT;
        }

        State<StateExtra> state = null;

        while (true) {
            operation = nextOperation();

            if (operation instanceof LoadOperation) {
                LoadOperation load = (LoadOperation) operation;

                world = CompUtil.parseEverything_fromString(rep, load.getModel());
                sigs = world.getAllSigs();
                command = world.getAllCommands().get(0);
                modelFilename = command.pos.filename;

                // Send back all the sigs
                writeMessage(Integer.toString(sigs.size()));
                for (Sig sig : sigs) {
                    writeMessage(sig.label);
                    writeMessage(multiplicity(sig));
                    writeMessage(sig instanceof Sig.PrimSig ? "" : removeCurly(sig.type().toString()));
                    CommandScope scope = command.getScope(sig);
                    if (scope == null) {
                        writeMessage("False");
                    } else {
                        writeMessage("True");
                        writeMessage(scope.startingScope);
                    }
                }
                
                // Send back the global scope
                writeMessage(Integer.toString(command.overall));
            } else if (operation instanceof ResolveOperation) {
                rep.minimizedBefore = 0;
                rep.minimizedAfter = 0;
                // Reexecute the command
                ans = TranslateAlloyToKodkod.execute_command(rep, world.getAllReachableSigs(), command, options);
            } else if (operation instanceof NextOperation) {
                if (ans.satisfiable()) {
                    writeMessage("True");
                    writeMessage(toXml(ans));

                    A4Solution nextAns = ans.next();
                    if (nextAns == ans) {
                        break;
                    }
                    ans = nextAns;
                } else {
                    writeMessage("False");
                }
            } else if (operation instanceof SetGlobalScopeOperation) {
                SetGlobalScopeOperation increaseScope = (SetGlobalScopeOperation) operation;
                int scopeSize = increaseScope.getScopeSize();

                command = setGlobalScope(scopeSize, command);
            } else if (operation instanceof SetScopeOperation) {
                SetScopeOperation increaseScope = (SetScopeOperation) operation;
                String sigName = increaseScope.getSig();
                int scopeSize = increaseScope.getScopeSize();

                Sig sig = findSig(sigName, sigs);
                command = setScopeSize(sig, scopeSize, command);
            } else if (operation instanceof SaveStateOperation) {
                state = saveState(sigs, new StateExtra(rep.minimizedBefore, rep.minimizedAfter, ans, command));
            } else if (operation instanceof RestoreStateOperation) {
                StateExtra extra = restoreState(state, sigs);
                rep.minimizedBefore = extra.getMinimizedBefore();
                rep.minimizedAfter = extra.getMinimizedAfter();
                ans = extra.getAnswer();
                command = extra.getCommand();
            } else if (operation instanceof RemoveConstraintOperation) {
                RemoveConstraintOperation removeConstraint = (RemoveConstraintOperation) operation;
                Pos constraint = removeConstraint.getPos(modelFilename);

                Command newCommand = removeGlobalConstraint(constraint, command);
                if (newCommand == null) {
                    if (!removeLocalConstraint(constraint, sigs)) {
                        throw new AlloyIGException(
                                String.format("Cannot remove constraint (line=%d, column=%d)-(line=%d, column=%d)",
                                constraint.y, constraint.x, constraint.y2, constraint.x2));
                    }
                } else {
                    command = newCommand;
                }
            } else if (operation instanceof UnsatCoreOperation) {
                // Without this check, the highLevelCore can return garbage.
                // Learned the hard way.
                if (rep.minimizedAfter > 0) {
                    Set<Pos> unsatCore = ans.highLevelCore().a;

                    writeMessage(unsatCore.size());
                    for (Pos unsate : unsatCore) {
                        writeMessage(unsate.y); // Line
                        writeMessage(unsate.x); // Column
                        writeMessage(unsate.y2); // Line
                        // The second column is one less than it should be.
                        // Not sure if Alloy bug or intended.
                        writeMessage(unsate.x2 + 1); // Column
                    }
                } else {
                    writeMessage(0);
                }
            } else if(operation instanceof SetUnsatCoreMinimizationOperation) {
                SetUnsatCoreMinimizationOperation setUnsatCoreMinimization = (SetUnsatCoreMinimizationOperation) operation;
                options.coreMinimization = setUnsatCoreMinimization.getMinimizationLevel();
            } else if (operation instanceof SetBitwidthOperation) {
                SetBitwidthOperation setBitwidthOp = (SetBitwidthOperation) operation;
                command = setBitwidth(setBitwidthOp.getBitwidth(), command);
            } else if (operation instanceof QuitOperation) {
                break;
            } else {
                throw new IllegalStateException("Unknown operation " + operation);
            }
        }
    }

    private static class StateExtra {

        private final int minimizedBefore;
        private final int minimizedAfter;
        private final A4Solution answer;
        private final Command command;

        public StateExtra(int minimizedBefore, int minimizedAfter, A4Solution answer, Command command) {
            this.minimizedBefore = minimizedBefore;
            this.minimizedAfter = minimizedAfter;
            this.answer = notNull(answer);
            this.command = notNull(command);
        }

        public int getMinimizedBefore() {
            return minimizedBefore;
        }

        public int getMinimizedAfter() {
            return minimizedAfter;
        }

        public A4Solution getAnswer() {
            return answer;
        }

        public Command getCommand() {
            return command;
        }
    }
}
