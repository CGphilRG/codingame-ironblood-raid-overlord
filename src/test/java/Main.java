import com.codingame.gameengine.runner.SoloGameRunner;
import com.codingame.gameengine.runner.simulate.GameResult;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        boolean simulate = false;
        int port = 8888;
        String pythonScript = null;
        String testCase = "test1.json";
        for (int i = 0; i < args.length; i++) {
            if ("--simulate".equals(args[i])) {
                simulate = true;
            } else if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            } else if ("--python".equals(args[i]) && i + 1 < args.length) {
                pythonScript = args[++i];
            } else if ("--test".equals(args[i]) && i + 1 < args.length) {
                testCase = args[++i];
            }
        }
        if (simulate) {
            runBatchSimulation(pythonScript);
            return;
        }
        if (!ensureSystemClasspath(args)) {
            return;
        }

        SoloGameRunner gameRunner = new SoloGameRunner();
        if (pythonScript != null) {
            gameRunner.setAgent("python3 " + new File(pythonScript).getAbsolutePath());
        } else {
            gameRunner.setAgent(resolveDefaultAgent());
        }
        gameRunner.setTestCase(testCase);
        gameRunner.start(port);
    }

    private static Class<?> resolveDefaultAgent() {
        try {
            return Class.forName("Solution");
        } catch (ClassNotFoundException e) {
            return StarterBot.class;
        }
    }

    private static boolean ensureSystemClasspath(String[] args) {
        if (ClassLoader.getSystemClassLoader().getResource("view") != null) {
            return true;
        }
        ClassLoader cl = Main.class.getClassLoader();
        if (cl instanceof URLClassLoader) {
            try {
                URL[] urls = ((URLClassLoader) cl).getURLs();
                StringBuilder cp = new StringBuilder();
                for (URL url : urls) {
                    if (cp.length() > 0) cp.append(File.pathSeparator);
                    cp.append(new File(url.toURI()).getAbsolutePath());
                }
                String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
                List<String> cmd = new ArrayList<>();
                cmd.add(javaBin);
                cmd.add("-cp");
                cmd.add(cp.toString());
                cmd.add(Main.class.getName());
                cmd.addAll(Arrays.asList(args));
                Process p = new ProcessBuilder(cmd).inheritIO().start();
                Runtime.getRuntime().addShutdownHook(new Thread(p::destroy));
                p.waitFor();
                return false;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    private static void runBatchSimulation(String pythonScript) {
        int wins = 0;
        int total = 0;
        String agentCmd = pythonScript != null ? "python3 " + new File(pythonScript).getAbsolutePath() : null;
        Class<?> defaultAgent = agentCmd == null ? resolveDefaultAgent() : null;
        for (int i = 1; i <= 50; i++) {
            String tcName = "test" + i + ".json";
            if (!new File("config", tcName).exists()) {
                continue;
            }
            total++;
            SoloGameRunner runner = new SoloGameRunner();
            if (agentCmd != null) {
                runner.setAgent(agentCmd);
            } else {
                runner.setAgent(defaultAgent);
            }
            runner.setTestCase(tcName);
            long t0 = System.nanoTime();
            GameResult result = runner.simulate();
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            int winScore = result.scores != null ? result.scores.getOrDefault(0, -1) : -1;
            if (winScore > 0) wins++;
            String meta = result.metadata == null ? "{}" : result.metadata.trim();
            System.out.printf("[Test %02d] status=%s Metadata=%s Frames=%d (%d ms)%n",
                i, winScore > 0 ? "WIN" : "LOSS", meta, result.views.size(), ms);
            if (winScore <= 0 && result.failCause != null) {
                System.out.printf("  failCause=%s%n", result.failCause);
            }
        }
        System.out.printf("==> Agent (%s) Wins: %d / %d%n",
            agentCmd != null ? agentCmd : defaultAgent.getSimpleName(), wins, total);
    }
}
