package tiefighter;

import agents.LARVAFirstAgent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import swing.LARVADash;
import java.util.ArrayList;
public class MyFirstTieFighter extends LARVAFirstAgent{

    enum Status {
        CHECKIN, CHECKOUT, OPENPROBLEM, COMISSION, JOIN, SOLVEPROBLEM, CLOSEPROBLEM, EXIT
    }
    enum OrientationLoop {
        NO,N,NE,O,X,E,SO,S,SE
    }
    
    
    Status mystatus;
    String service = "PManager", problem = "Tatooine",
            problemManager = "", content, sessionKey, sessionManager, storeManager, sensorKeys;
    int width, height, maxFlight;
    ACLMessage open, session;
    String[] contentTokens,
    mySensors = new String[] {
        "THERMAL",
        "GPS",
        "LIDAR",
        "ALTITUDE",
        "ENERGY",
        "ANGULAR",
        "VISUAL",
        "COMPASS"
    };
    boolean step = false, recargar = false;
    int tieOrientation = 0;
    int phase = 0;
    ArrayList<Punto> traza = new ArrayList<>();
    int maxTraza = 500;
    

    @Override
    public void setup() {
        super.setup();
        logger.onOverwrite();
        logger.setLoggerFileName("mylog.json");
        this.enableDeepLARVAMonitoring();
        Info("Setup and configure agent");
        mystatus = Status.CHECKIN;
        exit = false;
        myDashboard = new LARVADash(this);
        this.doActivateLARVADash();
   }

    @Override
    public void Execute() {
        Info("Status: " + mystatus.name());
        if (step) {
            step = this.Confirm("The next status will be " + mystatus.name() + "\n\nWould you like to continue step by step?");
        }
        switch (mystatus) {
            case CHECKIN:
                mystatus = MyCheckin();
                break;
            case OPENPROBLEM:
                mystatus = MyOpenProblem();
                break;
            case COMISSION:
                mystatus = MyComission();
            break;
            case JOIN:
                mystatus = MyJoin();
            break;
            case SOLVEPROBLEM:
                mystatus = MySolveProblem();
                break;
            case CLOSEPROBLEM:
                mystatus = MyCloseProblem();
                break;
            case CHECKOUT:
                mystatus = MyCheckout();
                break;
            case EXIT:
            default:
                exit = true;
                break;
        }
    }

    @Override
    public void takeDown() {
        Info("Taking down and deleting agent");
        this.saveSequenceDiagram("./" + this.problem + ".seqd");
        super.takeDown();
    }

    public Status MyCheckin() {
        Info("Loading passport and checking-in to LARVA");
        if (!loadMyPassport("./Passport/MyPassport.passport")) {
            Error("Unable to load passport file");
            return Status.EXIT;
        }
        if (!doLARVACheckin()) {
            Error("Unable to checkin");
            return Status.EXIT;
        }
        return Status.OPENPROBLEM;
    }

    public Status MyOpenProblem() {
        if (this.DFGetAllProvidersOf(service).isEmpty()) {
            Error("Service PMANAGER is down");
            return Status.CHECKOUT;
        }
        problemManager = this.DFGetAllProvidersOf(service).get(0);
        Info("Found problem manager " + problemManager);
        this.outbox = new ACLMessage();
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(problemManager, AID.ISLOCALNAME));
        outbox.setContent("Request open " + problem);
        this.LARVAsend(outbox);
        Info("Request opening problem " + problem + " to " + problemManager);
        open = LARVAblockingReceive();
        Info(problemManager + " says: " + open.getContent());
        content = open.getContent();
        contentTokens = content.split(" ");
        if (contentTokens[0].toUpperCase().equals("AGREE")) {
            sessionKey = contentTokens[4];
            session = LARVAblockingReceive();
            sessionManager = session.getSender().getLocalName();
            Info(sessionManager + " says: " + session.getContent());
            return Status.COMISSION;
        } else {
            Error(content);
            return Status.CHECKOUT;
        }
    }
    
    private int getMinPosOrientation(int[][] thermal, int[][] lidar, int [][]visual, double []gps) {
        int minReading = thermal[5][5]+10;
        int ori = 0, finalOri = 0;
        int finali = 0, finalj = 0;
        boolean cambia = false;
        int value = thermal[4][4];
        int x = (int)gps[0];
        int y = (int) gps[1];
        ArrayList<Punto> candidatos = new ArrayList<>();
        
        for(int i = 0; i < 9; i++){
            candidatos.add(null);
        }
        
        for(int i = 4; i < 7; i++){
            for(int j = 4; j < 7; j++){
                int x1 = x + (j-5);
                int y2 = y + (i-5);
                Punto p = new Punto(x1,y2);
                if((i != 5 || j != 5) && (thermal[i][j] <= minReading) && (visual[i][j] >=0)&& (visual[i][j] <= maxFlight) && !contiene(p)){
                    Punto p2 = new Punto(i,j);
                    if(thermal[i][j] < minReading){
                        for(int index = 0; index < candidatos.size(); index++){
                            candidatos.set(index,null);
                        }
                        minReading = thermal[i][j];
                        finalOri = ori;
                        finali = i;
                        finalj = j;
                    }
                    candidatos.set(ori, p2);
                }
                ori++;
                if(value != thermal[i][j]){
                    cambia = true;
                }
            }
        }
        
        System.out.println("Candidatos");
        int pasos, minPasos = 100;
        for(int i = 0; i < candidatos.size(); i++){
            if(candidatos.get(i) != null){
                System.out.println("Candidato: " + candidatos.get(i).x + " , " + candidatos.get(i).y);
                pasos = numPasos(i);
                if(pasos < minPasos){
                    minPasos = pasos;
                    finali = candidatos.get(i).x;
                    finalj = candidatos.get(i).y;
                    finalOri = i;
                }
                
            }
        }
        cambia=true;
        OrientationLoop enumValue = OrientationLoop.values()[finalOri];
        double angular = myDashboard.getAngular();
        System.out.println("Final_i: " + finali + "\nFinal_j: " + finalj + "\nAngular: " + angular 
                + "\nMinReading: " + minReading + "\nVisual: " + visual[finali][finalj] 
                + "\nLidar: " + lidar[finali][finalj]
                + "\nNumero traza: " + traza.size());
        if(!cambia){  
            System.out.println("No cambia");
            if(angular >= 0 && angular < 45 && (visual[5][6] >=0)){
                enumValue = OrientationLoop.E;
                finali = 5;
                finalj = 6;
            }
            else if(angular >= 45 && angular < 90 && (visual[4][6] >=0)){
                enumValue = OrientationLoop.NE;
                finali = 4;
                finalj = 6;
            }
            else if(angular >= 90 && angular < 135 && (visual[4][5] >=0)){
                enumValue = OrientationLoop.N;
                finali = 4;
                finalj = 5;
            }
            else if(angular >= 135 && angular < 180 && (visual[4][4] >=0)){
                enumValue = OrientationLoop.NO;
                finali = 4;
                finalj = 4;
            }
            else if(angular >= 180 && angular < 225 && (visual[5][4] >=0)){
                enumValue = OrientationLoop.O;
                finali = 5;
                finalj = 4;
            }
            else if(angular >= 225 && angular < 270 && (visual[6][4] >=0)){
                enumValue = OrientationLoop.SO;
                finali = 6;
                finalj = 4;
            }
            else if(angular >= 270 && angular < 315 && (visual[6][5] >=0)){
                enumValue = OrientationLoop.S;
                finali = 6;
                finalj = 5;
            }
            else if (angular >= 315 && angular < 360 && (visual[6][6] >=0)){
                enumValue = OrientationLoop.SE;
                finali = 6;
                finalj = 6;
            }
            else {
                enumValue = OrientationLoop.E;
                finali = 5;
                finalj = 6;
            }
        }
        
        if(lidar[finali][finalj] < 0 ){
            return -1;
        }
        
        switch(enumValue){
            case NO:
                return 135;
            case N:
                return 90;
            case NE:
                return 45;
            case O:
                return 180;
            case E:
                return 0;
            case SO:
                return 225;
            case S:
                return 270;
            case SE:
                return 315;
                
            case X:
            default:
                return 0;
        }
    }
    
    private int numPasos(int ori){
        int mappedOri;
        OrientationLoop enumOri = OrientationLoop.values()[ori];
        
        switch(enumOri){
            case NO:
                mappedOri = 135;
                break;
            case N:
                mappedOri = 90;
                break;
            case NE:
                mappedOri = 45;
                break;
            case O:
                mappedOri = 180;
                break;
            case E:
                mappedOri = 0;
                break;
            case SO:
                mappedOri = 225;
                break;
            case S:
                mappedOri = 270;
                break;
            case SE:
                mappedOri = 315;
                break;
                
            case X:
            default:
                mappedOri = 0;
        }
        
        if(mappedOri == tieOrientation){
            return 0;
        } else {
            int diff = Math.abs(mappedOri - tieOrientation);
            return diff/45;
        }
    }
    
    private boolean contiene(Punto p){
        boolean contiene = false;
        
        for(int i = 0; i < traza.size(); i++){
            if (!contiene){
                contiene = traza.get(i).compare(p);
            }
        }

        return contiene;
    }

    private void aniadir(Punto p){
        if (!contiene(p)){
            if (traza.size() >= maxTraza){
                traza.remove(0);
            }

            traza.add(p);
            System.out.println("Se inserta punto: " + p.x + " , " + p.y);
        }
    }

    private boolean WithOutSolution(int thermal[][], int visual[][]){
        boolean noSolution = false;
        boolean stop = false;
        for(int i = 0 ; i < thermal.length && !stop;i++){
            for (int j = 0; j < thermal.length;j++){
                if(thermal[i][j] == 0){
                    if(visual[i][j] == 255){
                        noSolution = true;
                        stop = true;
                    }
                }
            }  
        }
        return noSolution;
    }
    
    public Status MySolveProblem() {
        session = session.createReply();
        session.setContent("Query sensors session " + sessionKey);
        this.LARVAsend(session);
        session = this.LARVAblockingReceive();
        if(session.getContent().startsWith("Refuse") || session.getContent().startsWith("Failure")){
            Alert("Reading of sensors failed due to " + session.getContent());
            return Status.CLOSEPROBLEM;
        }
        
        int[][] thermal = myDashboard.getThermal();
        int[][] lidar = myDashboard.getLidar();
        int altitude = myDashboard.getAltitude();
        double energy = myDashboard.getEnergy();
        double[] gps = myDashboard.getGPS();
        int [][] visual = myDashboard.getVisual();
        // Comisionado para la interfaz pero no es necesario double compass = myDashboard.getCompass();
        
        session = session.createReply();
        String action = "";
        
        Punto p;
        p = new Punto((int)gps[0], (int)gps[1]);
        aniadir(p);
        
        if(WithOutSolution(thermal,visual)){
           Info("Jedi detected on unrecheable position. Returning to base");
           return Status.CLOSEPROBLEM; 
        }
        
        if(phase == 0){
            action = "RECHARGE";
            phase++;
        }
        
        else if(recargar){
            if (lidar[5][5] == 0){
                action = "RECHARGE";
                recargar = false;
            }
            else {
                action = "DOWN";
            }
        }
        else{
            if(thermal[5][5] == 0){
                if(altitude > 0){
                    action = "DOWN";
                }
                else{
                    action = "CAPTURE";
                }
            }
            else {
                int ori = getMinPosOrientation(thermal,lidar,visual, gps);
                System.out.println("Resultado funcion: " + ori);
                if((ori < 0) && (gps[2] < (maxFlight))){
                    action = "UP";
                    System.out.println("Altura: " + gps[2] + "\nMaxFlight: " + maxFlight);
                }
                /*else if (ori < 0){
                    action = "LEFT";
                    tieOrientation += 45;
                    tieOrientation = tieOrientation % 360;
                }*/
                else {
                    if(ori != tieOrientation){
                        if((ori - tieOrientation) >= 0){
                            action = "LEFT";
                            tieOrientation += 45;
                            tieOrientation = tieOrientation % 360;
                        }
                        else{
                            action = "RIGHT";
                            tieOrientation += 315;
                            tieOrientation = tieOrientation % 360;
                        }
                    }
                    else{
                        action = "MOVE";
                    }
                }

            }
            
        }
        
        double gasto = ((lidar[5][5]/5 * mySensors.length)+200);
        if (!recargar){
            recargar = energy < gasto;
        }
        //System.out.println("Recargar: " + recargar + "\nGasto estimado: " + gasto);
        session.setContent("Request execute " + action + " session " + sessionKey);
        this.LARVAsend(session);
        session = this.LARVAblockingReceive();
        if(session.getContent().startsWith("Refuse") || session.getContent().startsWith("Failure")){
            Alert("Reading of sensors failed due to " + session.getContent());
            return Status.CLOSEPROBLEM;
        }
        
        if(action.equals("CAPTURE")){
            return Status.CLOSEPROBLEM;
        }
        
        return Status.SOLVEPROBLEM;
    }
    
    public Status MyCloseProblem() {
        outbox = open.createReply();
        outbox.setContent("Cancel session " + sessionKey);
        Info("Closing problem " + problem + ", session " + sessionKey);
        this.LARVAsend(outbox);
        inbox = LARVAblockingReceive();
        Info(problemManager + " says: " + inbox.getContent());
        return Status.CHECKOUT;
    }

    public Status MyCheckout() {
        this.doLARVACheckout();
        return Status.EXIT;
    }
    
    public Status MyComission(){
        String localService = "STORE " + sessionKey;
        if(this.DFGetAllProvidersOf(localService).isEmpty()){
            Error("Service STORE is down");
            return Status.CLOSEPROBLEM;
        }
        
        storeManager = this.DFGetAllProvidersOf(localService).get(0);
        Info("Found store manager " + storeManager);
        
        sensorKeys= "";
        for(String s: mySensors) {
            outbox = new ACLMessage();
            outbox.setSender(this.getAID());
            outbox.addReceiver(new AID(storeManager, AID.ISLOCALNAME));
            outbox.setContent("Request product " + s + " session " + sessionKey);
            this.LARVAsend(outbox);
            inbox = this.LARVAblockingReceive();
            if(inbox.getContent().startsWith("Confirm")) {
                Info("Bought sensor " + s);
                sensorKeys += inbox.getContent().split(" ")[2] + " ";
            }
            else {
                this.Alert("Sensor " + s + " could not be obtained");
                return Status.CLOSEPROBLEM;
            }
        }
        
        Info("Sensor keys bought: " + sensorKeys);
        return Status.JOIN;
    }
    
    public Status MyJoin(){
        session = session.createReply();
        session.setContent("Request join session " + sessionKey + " attach sensors " + sensorKeys);
        this.LARVAsend(session);
        session = this.LARVAblockingReceive();
        String parse[] = session.getContent().split(" ");
        if(parse[0].equals("Confirm")) {
            width = Integer.parseInt(parse[8]);
            height = Integer.parseInt(parse[10]);
            maxFlight = Integer.parseInt(parse[14]);
            maxFlight = maxFlight -(maxFlight%5);
            return Status.SOLVEPROBLEM;
        }
        else {
            Alert("Error joining session: " + session.getContent());
            return Status.CLOSEPROBLEM;
        }
    }

}
