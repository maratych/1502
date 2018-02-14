import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;


import com.sun.deploy.util.StringUtils;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class xml_parser {
    public static void main(String[] args) {
        try {

                DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                Document document = documentBuilder.parse(new String());
                getText(document);

        } catch (XPathExpressionException | ParserConfigurationException | SAXException | IOException ex) {
            ex.printStackTrace(System.out);
        }

}

    public static String parse(String FilePath){
        String Text = "";
        try {

            DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = documentBuilder.parse(FilePath);
            Text = getText(document);


        } catch (XPathExpressionException | ParserConfigurationException | SAXException | IOException ex) {
            ex.printStackTrace(System.out);
        }

    return Text;
    }

// Получение всего текстового содержания статьи
public static String getText(Document document) throws DOMException, XPathExpressionException {
        XPathFactory pathFactory = XPathFactory.newInstance();
        XPath xpath = pathFactory.newXPath();

    List <String> TextList = new ArrayList<>();
    List <String> exprList = Arrays.asList("//textblock","//brief_title","//official_title","//ArticleTitle","//AbstractTex","//P","//ITEM_TITLE");
        for (int i = 0; i < exprList.size(); i++) {
            XPathExpression expr = xpath.compile(exprList.get(i));
            NodeList nodes = (NodeList) expr.evaluate(document, XPathConstants.NODESET);
            for (int j = 0; j < nodes.getLength(); j++) {
                Node n = nodes.item(j);
                String textblock = n.getTextContent();
                TextList.add(textblock);
            }
        }
        String Text = StringUtils.join(TextList, "\n");;
        //System.out.println(Text);
        return Text;
        }
}