package com.haulmont.yarg.formatters.impl.docx;

import com.haulmont.yarg.formatters.impl.AbstractFormatter;
import com.haulmont.yarg.formatters.impl.DocxFormatterDelegate;
import org.docx4j.TraversalUtil;
import org.docx4j.XmlUtils;
import org.docx4j.wml.P;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.Text;
import org.docx4j.wml.Tr;
import org.jvnet.jaxb2_commons.ppp.Child;

import java.util.*;
import java.util.regex.Matcher;

/**
* @author degtyarjov
* @version $Id$
*/
public class TableCollector extends TraversalUtil.CallbackImpl {
    private DocxFormatterDelegate docxFormatter;
    protected Stack<TableManager> currentTables = new Stack<TableManager>();
    protected Set<TableManager> tableManagers = new LinkedHashSet<TableManager>();
    protected boolean skipCurrentTable = false;

    public TableCollector(DocxFormatterDelegate docxFormatter) {this.docxFormatter = docxFormatter;}

    public List<Object> apply(Object object) {
        if (skipCurrentTable) return null;

        if (object instanceof Tr) {
            Tr currentRow = (Tr) object;
            final TableManager currentTable = currentTables.peek();

            if (currentTable.firstRow == null) {
                currentTable.firstRow = currentRow;

                findNameForCurrentTable(currentTable);

                if (currentTable.bandName == null) {
                    skipCurrentTable = true;
                } else {
                    tableManagers.add(currentTable);
                }
            }

            if (currentTable.rowWithAliases == null) {
                RegexpFinder aliasFinder = new RegexpFinder<P>(docxFormatter, AbstractFormatter.UNIVERSAL_ALIAS_PATTERN, P.class);
                new TraversalUtil(currentRow, aliasFinder);

                String foundAlias = aliasFinder.getValue();
                if (foundAlias != null && foundAlias.matches(AbstractFormatter.SIMPLE_ALIAS_REGEXP)) {
                    currentTable.rowWithAliases = currentRow;
                }
            }
        }

        return null;
    }

    protected void findNameForCurrentTable(final TableManager currentTable) {
        new TraversalUtil(currentTable.firstRow,
                new RegexpFinder<P>(docxFormatter, AbstractFormatter.BAND_NAME_DECLARATION_PATTERN, P.class) {
                    @Override
                    protected void onFind(P paragraph, Matcher matcher) {
                        if (currentTable.bandName == null) {
                            super.onFind(paragraph, matcher);
                            currentTable.bandName = matcher.group(1);
                            String bandNameDeclaration = matcher.group();
                            Set<Text> mergedTexts = new TextMerger(paragraph, bandNameDeclaration).mergeMatchedTexts();
                            for (Text text : mergedTexts) {
                                text.setValue(text.getValue().replace(bandNameDeclaration, ""));
                            }
                        }
                    }
                });
    }

    // Depth first
    public void walkJAXBElements(Object parent) {
        List children = getChildren(parent);
        if (children != null) {

            for (Object o : children) {
                o = XmlUtils.unwrap(o);

                if (o instanceof Child) {
                    ((Child) o).setParent(parent);
                }

                if (o instanceof Tbl) {
                    currentTables.push(new TableManager(docxFormatter, (Tbl) o));
                }

                this.apply(o);

                if (this.shouldTraverse(o)) {
                    walkJAXBElements(o);
                }

                if (o instanceof Tbl) {
                    currentTables.pop();
                    skipCurrentTable = false;
                }

            }
        }
    }
}
