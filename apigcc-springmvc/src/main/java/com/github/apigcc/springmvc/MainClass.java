package com.github.apigcc.springmvc;

import com.github.apigcc.core.Apigcc;
import com.github.apigcc.core.Context;
import com.github.apigcc.core.DirModule;
import com.github.apigcc.core.ExtConfig;
import com.github.apigcc.core.common.helper.FileHelper;
import com.github.apigcc.core.common.helper.StringHelper;
import com.github.apigcc.core.common.markup.MarkupBuilder;
import com.github.apigcc.core.common.markup.asciidoc.AsciiDoc;
import lombok.extern.slf4j.Slf4j;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.AttributesBuilder;
import org.asciidoctor.OptionsBuilder;
import org.asciidoctor.SafeMode;
import org.asciidoctor.jruby.AsciiDocDirectoryWalker;
import org.springframework.util.CollectionUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class MainClass {
    public static void main(String[] args) throws IOException {
        String extYamlPath = "D:\\opt\\apigcc\\apigcc.yaml";
        Yaml yaml = new Yaml(new Constructor(ExtConfig.class));
        ExtConfig extConfig = (ExtConfig) yaml.load(new FileInputStream(extYamlPath));
        List<Path> dirList = Files.walk(Paths.get(extConfig.getRootDir()), extConfig.getMaxDepth())
                .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(extConfig.getModules())) {
        } else {
            dirList = Files.walk(Paths.get(extConfig.getRootDir()), extConfig.getMaxDepth())
                    .filter(p -> extConfig.getModules().stream().anyMatch(m -> m.getDirName().equals(p.getFileName().toString()))).collect(Collectors.toList());
        }

        List<Path> jars = new ArrayList<>();
        extConfig.getJars().forEach(s -> jars.addAll(FileHelper.findJars(Paths.get(s))));
        List<MarkupBuilder> builderList = new ArrayList<>();
        for (Path path : dirList.stream().sorted().collect(Collectors.toList())) {
            Context context = new Context();
            String name = path.getFileName().toString();
            if (CollectionUtils.isEmpty(extConfig.getModules())) {
            }else {
                DirModule dirModule = extConfig.getModules().stream().filter(m -> m.getDirName().equals(name)).findFirst().get();
                context.setUrlPrefix(dirModule.getUrlPrefix());
            }
            context.setId(name);
            context.setName(name);
            context.addSource(path);
//            context.addDependency(path);
//            extConfig.getJars().forEach(s -> context.addJar(Paths.get(s)));
            jars.forEach(context::addJar);
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

            Boolean renderHtml = Apigcc.getInstance().getExtConfig().getRenderHtml();
            if (renderHtml != null && renderHtml) {
                //渲染adoc文件
                AttributesBuilder attributes = AttributesBuilder.attributes();
                attributes.sectionNumbers(true);
                attributes.noFooter(true);
                String css = Apigcc.getInstance().getContext().getCss();
                if (StringHelper.nonBlank(css)) {
                    attributes.linkCss(true);
                    attributes.styleSheetName(css);
                }
                //asciidoctorj 的 options
                OptionsBuilder builder = OptionsBuilder.options()
                        .mkDirs(true)
                        .inPlace(true)
                        .toDir(buildPath.toFile())
                        .safe(SafeMode.UNSAFE)
                        .attributes(attributes);
                Asciidoctor.Factory.create()
                        .convertDirectory(new AsciiDocDirectoryWalker(buildPath.toString()), builder.get());

                log.info("Render AsciiDoc to html {}", buildPath);
            }
        }
    }

}
