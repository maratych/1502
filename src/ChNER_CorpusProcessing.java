import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import uk.ac.cam.ch.wwmm.oscar.Oscar;
import uk.ac.cam.ch.wwmm.oscar.chemnamedict.entities.ResolvedNamedEntity;
import uk.ac.cam.ch.wwmm.oscar.document.NamedEntity;
import uk.ac.cam.ch.wwmm.oscar.document.TokenSequence;
import uk.ac.cam.ch.wwmm.oscar.types.NamedEntityType;
import uk.ac.cam.ch.wwmm.oscarMEMM.MEMMRecogniser;
import uk.ac.cam.ch.wwmm.oscarpattern.PatternRecogniser;
import gate.*;
import gate.creole.*;
import gate.util.*;
import gate.util.persistence.PersistenceManager;
import gate.corpora.RepositioningInfo;

public class ChNER_CorpusProcessing {

    public static void main(String[] args) throws IOException {
        Path textFile = Paths.get("foo.txt");
        OutputStreamWriter writerPattern = new OutputStreamWriter(new FileOutputStream("res/PatternRecogniser_res.tsv", false));
        writerPattern.write("Файл" + '\t'+"Сущность"+'\t'+"Оффсет"+'\t'+"Тип сущности"+'\t'+"Окно±25" +'\n');
        writerPattern.flush();
        OutputStreamWriter writerMEMM = new OutputStreamWriter(new FileOutputStream("res/MEMMRecogniser_res.tsv", false));
        writerMEMM.write("Файл" + '\t'+"Сущность"+'\t'+"Оффсет"+'\t'+"Тип сущности"+'\t'+"Окно±25" +'\n');
        writerPattern.flush();


        Path dirPath = Paths.get("./___Corpus");


        List <File> MyFiles = Files.walk(dirPath)
                .filter(Files::isRegularFile)
                .map(Path::toFile)
                .collect(Collectors.toList());
        Oscar oscar = new Oscar();
        PatternRecogniser patternrecogniser = new PatternRecogniser();
        //patternrecogniser.setDeprioritiseOnts(true);
        MEMMRecogniser memmRecogniser = new MEMMRecogniser();
        for (int j = 0; j < MyFiles.size(); j++) {
            String F = MyFiles.get(j).toString();
            String Text = xml_parser.parse(F);
            List<TokenSequence> tokSeqs = oscar.tokenise(Text);
            List<NamedEntity> entitiesP = patternrecogniser.findNamedEntities(tokSeqs);
            List<NamedEntity> entitiesM = memmRecogniser.findNamedEntities(tokSeqs);
            for (NamedEntity ne : entitiesP) {
                //System.out.println(ne.getConfidence());
                int windowL = 0;
                if (ne.getStart() > 25){
                    windowL = ne.getStart() - 25;
                }
                int windowR = Text.length();
                if (Text.length() > ne.getEnd() + 25){
                    windowR = ne.getEnd() + 25;
                }
                String NE_Window = Text.substring(windowL, windowR);
                NE_Window = NE_Window.replace('\n',' ').replace('\t',' ');
                String NE_clear = ne.getSurface();
                NamedEntityType NE_type = ne.getType();
                String NE_offset = Integer.toString(ne.getStart()) +" : "+ Integer.toString(ne.getEnd());

                try
                {
                    writerPattern.write(F + '\t'+NE_clear+'\t'+NE_offset+'\t'+NE_type+'\t'+NE_Window+'\n');
                    writerPattern.flush();
                }
                catch(IOException ex){

                    System.out.println(ex.getMessage());
                }
            }
            for (NamedEntity ne : entitiesM) {
                int windowL = 0;
                if (ne.getStart() > 25){
                    windowL = ne.getStart() - 25;
                }
                int windowR = Text.length();
                if (Text.length() > ne.getEnd() + 25){
                    windowR = ne.getEnd() + 25;
                }
                String NE_Window = Text.substring(windowL, windowR);
                NE_Window = NE_Window.replace('\n',' ').replace('\t',' ');
                String NE_clear = ne.getSurface();
                NamedEntityType NE_type = ne.getType();
                String NE_offset = Integer.toString(ne.getStart()) +" : "+ Integer.toString(ne.getEnd());

                try
                {
                    writerMEMM.write(F + '\t'+NE_clear+'\t'+NE_offset+'\t'+NE_type+'\t'+NE_Window+'\n');
                    writerMEMM.flush();
                }
                catch(IOException ex){

                    System.out.println(ex.getMessage());
                }
            }
        }


    }}
