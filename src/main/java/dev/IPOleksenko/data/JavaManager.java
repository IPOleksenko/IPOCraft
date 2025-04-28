package dev.IPOleksenko.data;

import javafx.scene.control.ListView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class JavaManager {
    private final ListView<String> javaListView;
    private final List<String> javaPaths = new ArrayList<>();

    public JavaManager(ListView<String> javaListView) {
        this.javaListView = javaListView;
    }

    public void loadJavaVersions() {
        javaListView.getItems().clear();
        javaPaths.clear();

        List<String> foundJavas = findJavaInstallations();
        javaListView.getItems().addAll(foundJavas);
        javaPaths.addAll(foundJavas);
    }

    private List<String> findJavaInstallations() {
        List<String> javaVersions = new ArrayList<>();
        String[] possiblePaths = {
                System.getenv("ProgramFiles") + "\\Java",
                System.getenv("ProgramFiles(x86)") + "\\Java",
                System.getenv("JAVA_HOME")
        };

        for (String path : possiblePaths) {
            if (path != null) {
                File dir = new File(path);
                if (dir.exists() && dir.isDirectory()) {
                    for (File subDir : dir.listFiles()) {
                        if (subDir.isDirectory() && (new File(subDir, "bin\\java.exe").exists())) {
                            javaVersions.add(subDir.getAbsolutePath());
                        }
                    }
                }
            }
        }

        return javaVersions;
    }

    public List<String> getJavaPaths() {
        return javaPaths;
    }

    public String getSelectedJavaPath(int index) {
        if (index >= 0 && index < javaPaths.size()) {
            return javaPaths.get(index);
        }
        return null;
    }
}
