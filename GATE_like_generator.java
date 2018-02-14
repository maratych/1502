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


public class StandAloneAnnie  {

    /** The Corpus Pipeline application to contain ANNIE */
    private CorpusController annieController;

    /**
     * Initialise the ANNIE system. This creates a "corpus pipeline"
     * application that can be used to run sets of documents through
     * the extraction system.
     */
    public void initAnnie() throws GateException, IOException {
        Out.prln("Initialising ANNIE...");

        // load the ANNIE application from the saved state in plugins/ANNIE
        File pluginsHome = Gate.getPluginsHome();
        File anniePlugin = new File(pluginsHome, "ANNIE");
        File annieGapp = new File(anniePlugin, "ANNIE_with_defaults.gapp");
        annieController =
                (CorpusController) PersistenceManager.loadObjectFromFile(annieGapp);

        Out.prln("...ANNIE loaded");
    } // initAnnie()

    /** Tell ANNIE's controller about the corpus you want to run on */
    public void setCorpus(Corpus corpus) {
        annieController.setCorpus(corpus);
    } // setCorpus

    /** Run ANNIE */
    public void execute() throws GateException {
        Out.prln("Running ANNIE...");
        annieController.execute();
        Out.prln("...ANNIE complete");
    } // execute()

    /**
     * Run from the command-line, with a list of URLs as argument.
     * <P><B>NOTE:</B><BR>
     * This code will run with all the documents in memory - if you
     * want to unload each from memory after use, add code to store
     * the corpus in a DataStore.
     */
    public static void main(String args[]) throws GateException, IOException {
        // initialise the GATE library
        if(Gate.getGateHome() == null)
            Gate.setGateHome(new File("/Applications/GATE_Developer_8.2"));
        if(Gate.getPluginsHome() == null)
            Gate.setPluginsHome(new File("/Applications/GATE_Developer_8.2/plugins"));
        Out.prln("Initialising GATE...");
        Gate.init();
        Out.prln("...GATE initialised");

        // initialise ANNIE (this may take several minutes)
        StandAloneAnnie annie = new StandAloneAnnie();
        annie.initAnnie();

        // create a GATE corpus and add a document for each command-line
        // argument
        Corpus corpus = Factory.newCorpus("StandAloneAnnie corpus");
        for(int i = 0; i < args.length; i++) {
            URL u = new URL(args[i]);
            FeatureMap params = Factory.newFeatureMap();
            params.put("sourceUrl", u);
            params.put("preserveOriginalContent", new Boolean(true));
            params.put("collectRepositioningInfo", new Boolean(true));
            Out.prln("Creating doc for " + u);
            Document doc = (Document)
                    Factory.createResource("gate.corpora.DocumentImpl", params);
            corpus.add(doc);
        } // for each of args

        // tell the pipeline about the corpus and run it
        annie.setCorpus(corpus);
        annie.execute();

        // for each document, get an XML document with the
        // person and location names added
        Iterator iter = corpus.iterator();
        int count = 0;
        String startTagPart_1 = "<span GateID=\"";
        String startTagPart_2 = "\" title=\"";
        String startTagPart_3 = "\" style=\"background:Red;\">";
        String endTag = "</span>";

        while(iter.hasNext()) {
            Document doc = (Document) iter.next();
            AnnotationSet defaultAnnotSet = doc.getAnnotations();
            Set annotTypesRequired = new HashSet();
            annotTypesRequired.add("Person");
            annotTypesRequired.add("Location");
            Set<Annotation> peopleAndPlaces =
                    new HashSet<Annotation>(defaultAnnotSet.get(annotTypesRequired));

            FeatureMap features = doc.getFeatures();
            String originalContent = (String)
                    features.get(GateConstants.ORIGINAL_DOCUMENT_CONTENT_FEATURE_NAME);
            RepositioningInfo info = (RepositioningInfo)
                    features.get(GateConstants.DOCUMENT_REPOSITIONING_INFO_FEATURE_NAME);

            ++count;
            File file = new File("StANNIE_" + count + ".HTML");
            Out.prln("File name: '"+file.getAbsolutePath()+"'");
            if(originalContent != null && info != null) {
                Out.prln("OrigContent and reposInfo existing. Generate file...");

                Iterator it = peopleAndPlaces.iterator();
                Annotation currAnnot;
                SortedAnnotationList sortedAnnotations = new SortedAnnotationList();

                while(it.hasNext()) {
                    currAnnot = (Annotation) it.next();
                    sortedAnnotations.addSortedExclusive(currAnnot);
                } // while

                StringBuffer editableContent = new StringBuffer(originalContent);
                long insertPositionEnd;
                long insertPositionStart;
                // insert anotation tags backward
                Out.prln("Unsorted annotations count: "+peopleAndPlaces.size());
                Out.prln("Sorted annotations count: "+sortedAnnotations.size());
                for(int i=sortedAnnotations.size()-1; i>=0; --i) {
                    currAnnot = (Annotation) sortedAnnotations.get(i);
                    insertPositionStart =
                            currAnnot.getStartNode().getOffset().longValue();
                    insertPositionStart = info.getOriginalPos(insertPositionStart);
                    insertPositionEnd = currAnnot.getEndNode().getOffset().longValue();
                    insertPositionEnd = info.getOriginalPos(insertPositionEnd, true);
                    if(insertPositionEnd != -1 && insertPositionStart != -1) {
                        editableContent.insert((int)insertPositionEnd, endTag);
                        editableContent.insert((int)insertPositionStart, startTagPart_3);
                        editableContent.insert((int)insertPositionStart,
                                currAnnot.getType());
                        editableContent.insert((int)insertPositionStart, startTagPart_2);
                        editableContent.insert((int)insertPositionStart,
                                currAnnot.getId().toString());
                        editableContent.insert((int)insertPositionStart, startTagPart_1);
                    } // if
                } // for

                FileWriter writer = new FileWriter(file);
                writer.write(editableContent.toString());
                writer.close();
            } // if - should generate
            else if (originalContent != null) {
                Out.prln("OrigContent existing. Generate file...");

                Iterator it = peopleAndPlaces.iterator();
                Annotation currAnnot;
                SortedAnnotationList sortedAnnotations = new SortedAnnotationList();

                while(it.hasNext()) {
                    currAnnot = (Annotation) it.next();
                    sortedAnnotations.addSortedExclusive(currAnnot);
                } // while

                StringBuffer editableContent = new StringBuffer(originalContent);
                long insertPositionEnd;
                long insertPositionStart;
                // insert anotation tags backward
                Out.prln("Unsorted annotations count: "+peopleAndPlaces.size());
                Out.prln("Sorted annotations count: "+sortedAnnotations.size());
                for(int i=sortedAnnotations.size()-1; i>=0; --i) {
                    currAnnot = (Annotation) sortedAnnotations.get(i);
                    insertPositionStart =
                            currAnnot.getStartNode().getOffset().longValue();
                    insertPositionEnd = currAnnot.getEndNode().getOffset().longValue();
                    if(insertPositionEnd != -1 && insertPositionStart != -1) {
                        editableContent.insert((int)insertPositionEnd, endTag);
                        editableContent.insert((int)insertPositionStart, startTagPart_3);
                        editableContent.insert((int)insertPositionStart,
                                currAnnot.getType());
                        editableContent.insert((int)insertPositionStart, startTagPart_2);
                        editableContent.insert((int)insertPositionStart,
                                currAnnot.getId().toString());
                        editableContent.insert((int)insertPositionStart, startTagPart_1);
                    } // if
                } // for

                FileWriter writer = new FileWriter(file);
                writer.write(editableContent.toString());
                writer.close();
            }
            else {
                Out.prln("Content : "+originalContent);
                Out.prln("Repositioning: "+info);
            }

            String xmlDocument = doc.toXml(peopleAndPlaces, false);
            String fileName = new String("StANNIE_toXML_" + count + ".HTML");
            FileWriter writer = new FileWriter(fileName);
            writer.write(xmlDocument);
            writer.close();

        } // for each doc
    } // main


    public static void mainOSCAR(String[] args) throws IOException {
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




    /**
     *
     */
    public static class SortedAnnotationList extends Vector {
        public SortedAnnotationList() {
            super();
        } // SortedAnnotationList

        public boolean addSortedExclusive(Annotation annot) {
            Annotation currAnot = null;

            // overlapping check
            for (int i=0; i<size(); ++i) {
                currAnot = (Annotation) get(i);
                if(annot.overlaps(currAnot)) {
                    return false;
                } // if
            } // for

            long annotStart = annot.getStartNode().getOffset().longValue();
            long currStart;
            // insert
            for (int i=0; i < size(); ++i) {
                currAnot = (Annotation) get(i);
                currStart = currAnot.getStartNode().getOffset().longValue();
                if(annotStart < currStart) {
                    insertElementAt(annot, i);
          /*
           Out.prln("Insert start: "+annotStart+" at position: "+i+" size="+size());
           Out.prln("Current start: "+currStart);
           */
                    return true;
                } // if
            } // for

            int size = size();
            insertElementAt(annot, size);
//Out.prln("Insert start: "+annotStart+" at size position: "+size);
            return true;
        } // addSorted
    } // SortedAnnotationList
} // class StandAloneAnnie
