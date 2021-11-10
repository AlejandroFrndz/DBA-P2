package tiefighter;

import agents.LARVAFirstAgent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import swing.LARVADash;

public class MyFirstTieFighter extends LARVAFirstAgent{

    enum Status {
        CHECKIN, CHECKOUT, OPENPROBLEM, COMISSION, JOIN, SOLVEPROBLEM, CLOSEPROBLEM, EXIT
    }
    enum OrientationLoop {
        NO,N,NE,O,X,E,SO,S,SE
    }
    
    Status mystatus;
    String service = "PManager", problem = "Hoth",
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
    
    private int getMinPosOrientation(int[][] thermal, int[][] lidar, int [][]visual) {
        int minReading = thermal[5][5];
        int ori = 0, finalOri = 0;
        int finali = 0, finalj = 0;
        boolean cambia = false;
        int value = thermal[4][4];
        
        for(int i = 4; i < 7; i++){
            for(int j = 4; j < 7; j++){
                if((i != 5 || j != 5) && (thermal[i][j] < minReading) && (visual[i][j] >=0)){
                    minReading = thermal[i][j];
                    finalOri = ori;
                    finali = i;
                    finalj = j;
                }
                ori++;
                if(value != thermal[i][j]){
                    cambia = true;
                }
            }
        }
        
        OrientationLoop enumValue = OrientationLoop.values()[finalOri];
        double angular = myDashboard.getAngular();
        if(!cambia){  
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
            else {
                enumValue = OrientationLoop.SE;
                finali = 6;
                finalj = 6;
            }
        }
        //System.out.println("Final_i: " + finali + "\nFinal_j: " + finalj + "\nAngular: " + angular);
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
        double compass = myDashboard.getCompass();
        
        session = session.createReply();
        String action = "";
        
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
                int ori = getMinPosOrientation(thermal,lidar,visual);
                System.out.println("Resultado funcion: " + ori);
                if((ori < 0) && (gps[2] < maxFlight)){
                    action = "UP";
                    System.out.println("Altura: " + gps[2]);
                }
                else if (ori < 0){
                    action = "LEFT";
                    tieOrientation += 45;
                    tieOrientation = tieOrientation % 360;
                }
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
    
        //Este resuelve el problema con el angular + onTarget
    /*public Status MySolveProblem() {
        session = session.createReply();
        session.setContent("Query sensors session " + sessionKey);
        this.LARVAsend(session);
        session = this.LARVAblockingReceive();
        if(session.getContent().startsWith("Refuse") || session.getContent().startsWith("Failure")){
            Alert("Reading of sensors failed due to " + session.getContent());
            return Status.CLOSEPROBLEM;
        }
        
        double angular = myDashboard.getAngular();
        boolean onTarget = myDashboard.getOnTarget();
        
        session = session.createReply();
        String action = "";
        
        if(onTarget){
            action = "CAPTURE";
        }
        else if(tieOrientation != angular){
            if(angular <= 180){
                action = "LEFT";
                tieOrientation += 45;
                tieOrientation = tieOrientation % 360;
            }
            else{
                action = "RIGHT";
                tieOrientation -= 45;
                tieOrientation = tieOrientation % 360;
            }
            action = "RIGHT";
        }
        else {
            action = "MOVE";
        }
        
        Info("Orientacion: " + tieOrientation);
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
    */
    
    public Status MyCloseProblem() {
        outbox = open.createReply();
        outbox.setContent("Cancel session " + sessionKey);
        Info("Closing problem Helloworld, session " + sessionKey);
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
            return Status.SOLVEPROBLEM;
        }
        else {
            Alert("Error joining session: " + session.getContent());
            return Status.CLOSEPROBLEM;
        }
    }

}
