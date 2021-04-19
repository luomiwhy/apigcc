package com.github.apigcc.springmvc;

import com.github.apigcc.core.Apigcc;
import com.github.apigcc.core.Context;
import com.github.apigcc.core.DirModule;
import com.github.apigcc.core.ExtConfig;
import com.github.apigcc.core.common.helper.FileHelper;
import com.github.apigcc.core.common.markup.MarkupBuilder;
import com.github.apigcc.core.common.markup.asciidoc.AsciiDoc;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class MainClass {
    public static void main(String[] args) throws IOException {
        String extYamlPath = "apigcc.yaml";
        Yaml yaml = new Yaml(new Constructor(ExtConfig.class));
        ExtConfig extConfig = (ExtConfig) yaml.load(new FileInputStream(extYamlPath));
        List<Path> dirList = Files.walk(Paths.get(extConfig.getRootDir()), extConfig.getMaxDepth())
                .filter(p -> extConfig.getModules().stream().anyMatch(m -> m.getDirName().equals(p.getFileName().toString()))).collect(Collectors.toList());

        List<MarkupBuilder> builderList = new ArrayList<>();
        for (Path path : dirList) {
            String name = path.getFileName().toString();
            DirModule dirModule = extConfig.getModules().stream().filter(m -> m.getDirName().equals(name)).findFirst().get();
            Context context = new Context();
            context.setId(name);
            context.setName(name);
            context.setUrlPrefix(dirModule.getUrlPrefix());
            context.addSource(path);
            context.addDependency(path);
            extConfig.getJars().forEach(s -> context.addJar(Paths.get(s)));
            context.setBuildPath(Paths.get(extConfig.getBuildPath()));

            Apigcc apigcc = new Apigcc(context);
            apigcc.setExtConfig(extConfig);
            apigcc.parse();
            apigcc.render();
            builderList.addAll(apigcc.getMarkupBuilderList());
        }
        final Boolean mergeToOneFile = Optional.ofNullable(extConfig.getMergeToOneFile()).orElse(Boolean.FALSE);
        if (mergeToOneFile && !builderList.isEmpty()) {
            Path buildPath = Apigcc.getInstance().getContext().getBuildPath();
            Path adocFile = buildPath.resolve(extConfig.getMergeFileName() + AsciiDoc.EXTENSION);
            FileHelper.write(adocFile, builderList.stream().map(MarkupBuilder::getContent).collect(Collectors.joining(System.lineSeparator())));
            log.info("Build AsciiDoc append to one file {}", adocFile);
        }
    }

}
