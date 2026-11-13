package com.shiroha.mmdskin.asset.catalog;

import com.shiroha.mmdskin.config.PathConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;

/** 文件职责：扫描并缓存本地模型目录的元数据条目。 */
public class ModelCatalogEntry {
    private static final Logger logger = LogManager.getLogger();

    private record CacheSnapshot(List<ModelCatalogEntry> models, long timestamp) {}
    private static volatile CacheSnapshot cache = null;
    private static final long CACHE_TTL = 5000;

    private final String folderName;
    private final String folderPath;
    private final String modelFilePath;
    private final String modelFileName;
    private final boolean isPMD;
    private final boolean isVRM;
    private final long fileSize;
    private final boolean card;

    public ModelCatalogEntry(String folderName, String folderPath, String modelFilePath, String modelFileName, boolean isPMD, boolean isVRM, long fileSize) {
        this(folderName, folderPath, modelFilePath, modelFileName, isPMD, isVRM, fileSize, false);
    }

    public ModelCatalogEntry(String folderName, String folderPath, String modelFilePath, String modelFileName,
                             boolean isPMD, boolean isVRM, long fileSize, boolean card) {
        this.card = card;
        this.folderName = folderName;
        this.folderPath = folderPath;
        this.modelFilePath = modelFilePath;
        this.modelFileName = modelFileName;
        this.isPMD = isPMD;
        this.isVRM = isVRM;
        this.fileSize = fileSize;
    }

    public String getFolderName() { return folderName; }
    public String getDisplayName() { return folderName; }
    public String getFolderPath() { return folderPath; }
    public String getModelFilePath() { return modelFilePath; }
    public String getModelFileName() { return modelFileName; }
    public boolean isPMD() { return isPMD; }
    public boolean isVRM() { return isVRM; }
    public long getFileSize() { return fileSize; }
    /** True for a model that comes from a PNG card in the Cards folder. */
    public boolean isCard() { return card; }

    /** Prefix of the catalog key of card models, which keeps them apart from plain model folders. */
    public static final String CARD_PREFIX = "[Card] ";

    public static boolean isCardKey(String modelName) {
        return modelName != null && modelName.startsWith(CARD_PREFIX);
    }
    public String getCatalogKey() { return folderName; }

    public String getFormattedSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        }
    }

    public String getFormatDescription() {
        if (isVRM) {
            String name = modelFileName.toLowerCase(java.util.Locale.ROOT);
            return name.endsWith(".glb") ? "GLB" : name.endsWith(".gltf") ? "glTF" : "VRM";
        }
        return isPMD ? "PMD" : "PMX";
    }

    public static List<ModelCatalogEntry> scanModels() {
        CacheSnapshot snapshot = cache;
        long now = System.currentTimeMillis();
        if (snapshot != null && (now - snapshot.timestamp()) < CACHE_TTL) {
            return snapshot.models();
        }

        List<ModelCatalogEntry> models = new ArrayList<>();
        // Cards are listed first; the selector shows them in their own highlighted group.
        List<ModelCatalogEntry> cards = scanCards();
        models.addAll(cards);

        File entityPlayerDir = PathConstants.getEntityPlayerDir();
        File[] modelDirs = entityPlayerDir.isDirectory() ? entityPlayerDir.listFiles(File::isDirectory) : null;
        if (modelDirs == null) {
            logger.warn("EntityPlayer folder not found: {}", entityPlayerDir.getAbsolutePath());
            cache = new CacheSnapshot(List.copyOf(models), System.currentTimeMillis());
            return cache.models();
        }
        int folderStart = models.size();

        for (File modelDir : modelDirs) {
            ModelCatalogEntry info = scanModelFolder(modelDir);
            if (info != null) {
                models.add(info);
                logger.debug("发现模型: {} -> {}", info.getFolderName(), info.getModelFileName());
            }
        }

        models.subList(folderStart, models.size()).sort((a, b) -> a.getFolderName().compareToIgnoreCase(b.getFolderName()));
        cache = new CacheSnapshot(List.copyOf(models), System.currentTimeMillis());
        return cache.models();
    }

    /**
     * Lists the PNG cards in {@code 3d-skin/Cards} and extracts each one (once) into
     * {@code 3d-skin/Cards/.extracted}. Broken cards are skipped with a log line.
     */
    private static List<ModelCatalogEntry> scanCards() {
        List<ModelCatalogEntry> cards = new ArrayList<>();
        File cardDir = PathConstants.getCardDir();
        File[] files = cardDir.isDirectory()
                ? cardDir.listFiles(f -> f.isFile() && f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".png"))
                : null;
        if (files == null) {
            return cards;
        }
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        java.nio.file.Path cache = PathConstants.getCardCacheDir().toPath();
        for (File file : files) {
            if (!ModelCardFile.isCard(file.toPath())) {
                logger.warn("Not a 3D Model Renderer card (Koikatsu cards must be converted in the studio): {}", file.getName());
                continue;
            }
            try {
                ModelCardFile.Extracted card = ModelCardFile.extract(file.toPath(), cache);
                String stem = file.getName().substring(0, file.getName().length() - 4);
                File model = card.modelFile().toFile();
                String type = card.modelType();
                cards.add(new ModelCatalogEntry(CARD_PREFIX + stem, model.getParentFile().getAbsolutePath(),
                        model.getAbsolutePath(), model.getName(), type.equals("pmd"),
                        type.equals("vrm") || type.equals("glb") || type.equals("gltf"), file.length(), true));
            } catch (java.io.IOException | RuntimeException e) {
                logger.warn("Could not read card {}: {}", file.getName(), e.getMessage());
            }
        }
        return cards;
    }

    public static void invalidateCache() {
        cache = null;
    }

    private static ModelCatalogEntry scanModelFolder(File modelDir) {
        FileFilter pmxFilter = file -> file.isFile() && file.getName().toLowerCase().endsWith(".pmx");
        FileFilter pmdFilter = file -> file.isFile() && file.getName().toLowerCase().endsWith(".pmd");
        // .glb and .gltf are loaded through the same glTF path as .vrm; a plain glTF simply has no VRM metadata.
        FileFilter vrmFilter = file -> {
            String name = file.getName().toLowerCase();
            return file.isFile() && (name.endsWith(".vrm") || name.endsWith(".glb") || name.endsWith(".gltf"));
        };

        File[] pmxFiles = modelDir.listFiles(pmxFilter);
        if (pmxFiles != null && pmxFiles.length > 0) {
            File selected = findPreferredModel(pmxFiles);
            return new ModelCatalogEntry(modelDir.getName(), modelDir.getAbsolutePath(),
                    selected.getAbsolutePath(), selected.getName(), false, false, selected.length());
        }

        File[] pmdFiles = modelDir.listFiles(pmdFilter);
        if (pmdFiles != null && pmdFiles.length > 0) {
            File selected = findPreferredModel(pmdFiles);
            return new ModelCatalogEntry(modelDir.getName(), modelDir.getAbsolutePath(),
                    selected.getAbsolutePath(), selected.getName(), true, false, selected.length());
        }

        File[] vrmFiles = modelDir.listFiles(vrmFilter);
        if (vrmFiles != null && vrmFiles.length > 0) {
            File selected = findPreferredModel(vrmFiles);
            return new ModelCatalogEntry(modelDir.getName(), modelDir.getAbsolutePath(),
                    selected.getAbsolutePath(), selected.getName(), false, true, selected.length());
        }

        return null;
    }

    private static File findPreferredModel(File[] files) {
        if (files.length == 1) {
            return files[0];
        }
        for (File file : files) {
            String name = file.getName().toLowerCase();
            if (name.equals("model.pmx") || name.equals("model.pmd")) {
                return file;
            }
        }
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return files[0];
    }

    public static ModelCatalogEntry findByFolderName(String folderName) {
        List<ModelCatalogEntry> models = scanModels();
        for (ModelCatalogEntry info : models) {
            if (info.getFolderName().equals(folderName)) {
                return info;
            }
        }
        return null;
    }
}
