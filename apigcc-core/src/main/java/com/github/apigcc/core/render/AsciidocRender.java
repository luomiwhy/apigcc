package com.github.apigcc.core.render;

import com.github.apigcc.core.Apigcc;
import com.github.apigcc.core.common.helper.FileHelper;
import com.github.apigcc.core.common.helper.StringHelper;
import com.github.apigcc.core.common.markup.MarkupBuilder;
import com.github.apigcc.core.common.markup.asciidoc.AsciiDoc;
import com.github.apigcc.core.schema.*;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.AttributesBuilder;
import org.asciidoctor.OptionsBuilder;
import org.asciidoctor.SafeMode;
import org.asciidoctor.jruby.AsciiDocDirectoryWalker;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 构建并渲染adoc文档
 */
@Slf4j
public class AsciidocRender implements ProjectRender {

    public static final CharSequence[] attrs = Lists.newArrayList(
            AsciiDoc.attr(AsciiDoc.DOCTYPE, AsciiDoc.BOOK),
            AsciiDoc.attr(AsciiDoc.TOC, AsciiDoc.LEFT), AsciiDoc.attr(AsciiDoc.TOC_LEVEL, 2), AsciiDoc.attr(AsciiDoc.TOC_TITLE, "TOC"),
            AsciiDoc.attr(AsciiDoc.SOURCE_HIGHLIGHTER, AsciiDoc.HIGHLIGHTJS)).toArray(new CharSequence[0]);

    @Override
    public void render(Project project) {

        Path buildPath = Apigcc.getInstance().getContext().getBuildPath();
        Path projectBuildPath = buildPath.resolve(project.getId());
        String id = Apigcc.getInstance().getContext().getId();

        project.getBooks().forEach((name, book) -> {
            MarkupBuilder builder = MarkupBuilder.getInstance();
            String displayName = project.getName();
            if(!Objects.equals(Book.DEFAULT, name)){
                displayName += " - " + name;
            }
            builder.header(displayName, attrs);
            if (Objects.nonNull(project.getVersion())) {
                builder.paragraph("version:" + project.getVersion());
            }
            builder.paragraph(project.getDescription());
            for (Chapter chapter : book.getChapters()) {
                if (chapter.isIgnore() || chapter.getSections().isEmpty()) {
                    continue;
                }
                Set<Section> sectionSet = chapter.getSections().stream().filter(this::shouldRender).collect(Collectors.toSet());
                if (sectionSet.isEmpty()) {
                    continue;
                }
                builder.title(1, chapter.getName());
                builder.paragraph(chapter.getDescription());
                for (Section section : sectionSet) {
                    if(section.isIgnore()){
                        continue;
                    }
                    builder.title(2, section.getName());
                    builder.paragraph(section.getDescription());


                    builder.title(4, "request");
                    builder.listing(b -> {
                        b.textLine(section.getRequestLine());
                        section.getInHeaders().values().forEach(header -> {
                            Optional<Tag> tag = section.tag("param:" + header.getKey());
                            builder.textLine("header: " + header.toString() + "  " + (tag.isPresent() ? tag.get().getContent() : ""));
                        });
                        if (section.hasRequestBody()) {
                            b.br();
                            b.text(section.getParameterString());
                        }
                    }, "source,HTTP");

                    table(builder, section.getRequestRows().values());

                    builder.title(4, "response");

                    builder.listing(b -> {
                        section.getOutHeaders().values().forEach(header -> builder.textLine("header: " + header.toString()));
                        if (section.hasResponseBody()) {
                            b.br();
                            b.text(section.getResponseString());
                        }else{
                            b.text("N/A");
                        }
                    }, "source,JSON");

                    table(builder, section.getResponseRows().values());

                }
            }

            Path adocFile = projectBuildPath.resolve(id + "_" + name + AsciiDoc.EXTENSION);
            FileHelper.write(adocFile, builder.getContent());
            log.info("Build AsciiDoc {}", adocFile);
        });

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
                    .toDir(projectBuildPath.toFile())
                    .safe(SafeMode.UNSAFE)
                    .attributes(attributes);
            Asciidoctor.Factory.create()
                    .convertDirectory(new AsciiDocDirectoryWalker(projectBuildPath.toString()), builder.get());

            log.info("Render AsciiDoc to html {}", projectBuildPath);
        }
    }

    private void table(MarkupBuilder builder, Collection<Row> rows) {
        if (rows.size() > 0) {
            List<List<String>> responseTable = new ArrayList<>();
            responseTable.add(Lists.newArrayList("Key", "Type", "Condition", "Def", "Remark"));
            rows.forEach(row -> responseTable.add(Lists.newArrayList(row.getKey(), row.getType(), row.getCondition(), row.getDef(), row.getRemark())));
            builder.table(responseTable, true, false);
        }
    }

}
