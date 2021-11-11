/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tiefighter;

/**
 *
 * @author jlope
 */
public class Punto {
    
    int x;
    int y;
    public Punto (int x, int y){
        this.x = x;
        this.y = y;
    }
    
    public boolean compare (Punto otro){
        boolean res = false;
        
        if ((x == otro.x) && (y == otro.y)){
            res = true;
        }
        return res;
    }
    
}
