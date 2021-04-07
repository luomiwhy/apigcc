package com.github.apigcc.springmvc;

import com.github.apigcc.core.Apigcc;
import com.github.apigcc.core.Context;
import com.github.apigcc.core.DirModule;
import com.github.apigcc.core.ExtConfig;
import com.github.apigcc.core.common.diff.FileMatcher;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class SpringTest {

    @Test
    public void t() throws IOException {
//
//        Path path = Paths.get("org/springframework/data/domain/Page.java");
//        FileHelper.write(path, code);
//
//        JavaParserTypeSolver javaParserTypeSolver = new JavaParserTypeSolver(path);
//
//        CompilationUnit cu = StaticJavaParser.parse(path);
//
//        System.out.println(cu);

    }



    @Test
    public void test() throws IOException {
        String extYamlPath = "D:\\opt\\apigcc\\urlOnly.yaml";
        Yaml yaml = new Yaml(new Constructor(ExtConfig.class));
        ExtConfig extConfig = (ExtConfig) yaml.load(new FileInputStream(extYamlPath));
        List<Path> dirList = Files.walk(Paths.get(extConfig.getRootDir()), extConfig.getMaxDepth())
                .filter(p -> extConfig.getModules().stream().anyMatch(m -> m.getDirName().equals(p.getFileName().toString()))).collect(Collectors.toList());

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
        }
    }


    @Test
    public void testTestToolls() throws IOException {

        Context context = new Context();
        context.setId("test-tools");
        context.setName("测试工具");
        context.addSource(Paths.get("D:/workspaces/ubisor-test-tools/backend/"));
//        context.setCss("https://darshandsoni.com/asciidoctor-skins/css/monospace.css");

        Apigcc apigcc = new Apigcc(context);
        apigcc.parse();
        apigcc.render();

        Path buildAdoc = Paths.get("build/test-tools/index.adoc");
        Path template = Paths.get("src/test/resources/test-tools.adoc");
        Path templateHtml = Paths.get("src/test/resources/template.html");
        Path resultHtml = Paths.get("build/test-tools/diff.html");

        FileMatcher fileMatcher = new FileMatcher();
        int changed = fileMatcher.compare(template, buildAdoc);
        if(changed>0){
            fileMatcher.rederHtml(templateHtml, resultHtml);
        }

        System.out.println("BUILD SUCCESS");
    }


    @Test
    public void testUbcloud() throws IOException {

        Context context = new Context();
        context.setId("ubcloud");
        context.setName("优碧云1");
        context.addSource(Paths.get("D:/workspaces/ubisor-backend/ubcloud-front-web/"));
        context.addDependency(Paths.get("D:/workspaces/ubisor-backend/"));
        context.setCss("https://darshandsoni.com/asciidoctor-skins/css/monospace.css");

        Apigcc apigcc = new Apigcc(context);
        apigcc.parse();
        apigcc.render();

        Path buildAdoc = Paths.get("build/ubcloud/index.adoc");
        Path template = Paths.get("src/test/resources/ubcloud-front-web.adoc");
        Path templateHtml = Paths.get("src/test/resources/template.html");
        Path resultHtml = Paths.get("build/ubcloud/diff.html");

        FileMatcher fileMatcher = new FileMatcher();
        int changed = fileMatcher.compare(template, buildAdoc);
        if(changed>0){
            fileMatcher.rederHtml(templateHtml, resultHtml);
        }

        System.out.println("BUILD SUCCESS");
    }

}