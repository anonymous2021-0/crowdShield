
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Field;
import it.unisa.dia.gas.jpbc.Pairing;
import it.unisa.dia.gas.plaf.jpbc.pairing.PairingFactory;

public class SignAndUpload {
    private JPanel main;
    private JButton buttonFile;
    private JButton buttonSign;
    private JButton buttonUpload;
    private JComboBox comboBox1;
    private JPanel pannelL1;
    private JPanel pannelR1;
    private JPanel pannelL21;
    private JPanel pannelL22;
    private JPanel pannelL3;
    private JPanel pannelL4;
    private JPanel pannelR2;
    private JLabel labelImage;
    private JLabel labelImageCap;
    private JLabel labelOutputCap;
    private JLabel labelSecure;
    private JTextArea textAreaOutput;

    JFrame popFrame=null;
    BufferedImage bi=null;

    public int numUser = 100;
    private int thisUserID;// randomly assigning an ID.
    private double privacyRiskFactor;// choose the risk level that this user can accept.
    public double inferCapability = 0.8;
    public int numCoLocatedPhotos = 7;
    private int groupSize;
    private Pairing bp = PairingFactory.getPairing("a.properties");
    private Field G2 = bp.getG2();
    private Field Zr = bp.getZr();
    private Element g = G2.newRandomElement().getImmutable();
    private ArrayList<Element> privateKeys = new ArrayList<>(numUser);
    private ArrayList<Element> publicKeys = new ArrayList<>(numUser);
    private ArrayList<Element> ringSignatures = new ArrayList<>(numUser);
    private byte[] imgData = null;
    private String imgPath = null;

    public SignAndUpload() {
        buttonFile.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("打开文件:");
                openFile();
            }
        });
        buttonSign.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (imgPath==null){
                    JOptionPane.showMessageDialog(null, "For signature generation.\r\nPLS Select a photo first!", "WARNING_MESSAGE", JOptionPane.WARNING_MESSAGE);
                }
                else {
                    long t_generation = ringSigning();
                    int sigSize = ringSignatures.get(0).duplicate().toBytes().length * ringSignatures.size();

                    DecimalFormat df = new DecimalFormat("#.0");
                    String print = "----------------Signature generation success, ready to share?----------------\r\nImage Size: "
                            + imgData.length + " bytes\r\nPrivacy Risk Factor: " + df.format(privacyRiskFactor) + "\r\nGroup Size: "
                            + groupSize + "/"+ numUser +" users in your group\r\nSignature Size: " + sigSize + " bytes\r\nGeneration time: "
                            + t_generation + " ms\r\nSignature in form:[(" + ringSignatures.get(0) + "),......]";
                    textAreaOutput.setText(print);
                }
            }
        });
        //The interaction with the cloud environment is not implemented
        //Using the validation process that ought to be performed by the platform as the uploading process
        buttonUpload.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (imgPath==null){
                    JOptionPane.showMessageDialog(null, "For signature generation.\r\nPLS Select a photo first!", "WARNING_MESSAGE", JOptionPane.WARNING_MESSAGE);
                }
                else if (ringSignatures.size()==0){
                    JOptionPane.showMessageDialog(null, "NOT signing yet.\r\nyour identity may be disclosed!", "WARNING_MESSAGE", JOptionPane.WARNING_MESSAGE);
                }
                else {
                    long t0 = System.currentTimeMillis();
                    boolean boolValidate = validate();
                    long t1 = System.currentTimeMillis();
                    long t_validate = t1 - t0;

                    if (boolValidate) {
                        textAreaOutput.setText("Simulated Uploading Completed!\r\n\r\nSignature Validating Time: " + t_validate + "ms");
                    } else {
                        textAreaOutput.setText("Validation Failure!\r\nSomething Wrong Happens!");
                    }
                }
            }
        });
        comboBox1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                privacyRiskFactor = (comboBox1.getSelectedIndex()+1)*0.10;
                System.out.println(privacyRiskFactor);
                double[] privacyArray= new double[numUser];
                for (int i = 0 ;i < numUser; i++){
                    if(i == thisUserID){
                        privacyArray[i]=privacyRiskFactor;
                    }
                    // Simulating that all the other users set a privacy risk factor. Values randomly selected.
                    else{
                        double tmpPrivacyRiskFactor = (new Random().nextInt(5) + 1) * 0.10;
                        privacyArray[i]=tmpPrivacyRiskFactor;
                    }
                }
                int[] group = grouping(privacyArray);
                System.out.println(group[0]);
                System.out.println(group[1]);
                System.out.println(group[2]);
                System.out.println(group[3]);
                System.out.println(group[4]);
            }
        });
    }

    public int[] grouping(double[] privacyArray){
        Integer[] numCover = new Integer[privacyArray.length];
        int thisNumCover = 0;
        for(int i = 0; i < privacyArray.length; i++){
            numCover[i] = new Double(Math.ceil(inferCapability/(1 - Math.pow((1 - privacyArray[i]), 1./numCoLocatedPhotos)))).intValue();
            if (i == thisUserID){
                thisNumCover = numCover[i];
            }
        }
        Arrays.sort(numCover, Collections.reverseOrder());
        int[] group = new int[numCover.length];
        int it = 0;
        int flag = 0;
        for(int i = 0; i < numCover.length; i++){
            if(i+numCover[i] <= numCover.length) {
                group[it] = numCover[i];
                if (flag ==0) {
                    if (thisNumCover == numCover[i]) {
                        groupSize = group[it];
                        flag = 1;
                    } else if (thisNumCover > numCover[i]) {
                        groupSize = group[it - 1];
                        flag = 1;
                    }
                }
                i = i + numCover[i] - 1;
                it = it + 1;
            }
            else{
                group[it] = numCover.length - i;
                if (flag == 0) {
                    if (thisNumCover == numCover[i]) {
                        groupSize = group[it];
                        flag = 1;
                    } else if (thisNumCover > numCover[i]) {
                        groupSize = group[it - 1];
                        flag = 1;
                    }
                }
                group[it + 1] = -1;
                break;
            }
        }
        return group;
    }

    public byte[] readImg(String path){
        File file = new File(path);
        byte[] img_content = null;
        try {
            FileInputStream fis = new FileInputStream(file);
            img_content = new byte[(int) file.length()];
            fis.read(img_content);
            fis.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return img_content;
    }

    //read and present an image
    public void openFile(){
        JFileChooser chooser=new JFileChooser();
        chooser.showOpenDialog(popFrame);//open a file selector
        File f = chooser.getSelectedFile();
        //String FilePath=f.getAbsolutePath();
        //whether the file existed
        if(f == null) {
            return;
        }
        try {
            bi = ImageIO.read(f);
            int width = 0;
            int height = 0;
            double ratio = 355./220;
			/*A image file?
			whether it has width or length property*/
            if(bi == null || bi.getHeight() <=0 || bi.getWidth() <=0){
                labelImage.setText("Not a validate image, pls choose again!");
                return;
            } else {
                String path = f.getPath();
                System.out.println(path);
                imgPath = path;

                ImageIcon imageFile = new ImageIcon(path);
                double ratioFact = ((double) bi.getWidth()) /((double) bi.getHeight());
                if (ratio < ratioFact){
                    width = 350;
                    height = (int) Math.ceil(width/ratioFact);
                }
                else{
                    height = 220;
                    width = (int) (height*ratioFact);
                }

                imageFile.setImage(imageFile.getImage().getScaledInstance(width, height, Image.SCALE_DEFAULT));
                labelImage.setIcon(imageFile);
            }
        } catch (IOException e) {
            //e.printStackTrace();
            return;
        }
    }

    public long ringSigning(){
        imgData = readImg(imgPath);
        thisUserID = new Random().nextInt(groupSize);

        long t0 = System.currentTimeMillis();
        Element h = G2.newElement();
        h.setFromHash(imgData,0,imgData.length);  //Hash the plaintext and map it to G2

        ringSignatures = new ArrayList<>(numUser);
        int flag = 0;
        Element factorMul = null;
        //for (int i = 0; i < numUser; i++){
        for (int i = 0; i < groupSize; i++){
            Element a = Zr.newRandomElement().getImmutable();
            Element otherSign = g.powZn(a).getImmutable();
            ringSignatures.add(otherSign);
            if(i != thisUserID){
                Element va = publicKeys.get(i).powZn(a).getImmutable();
                if(flag == 0){
                    factorMul = va;
                    flag = 1;
                }
                else{
                    factorMul = factorMul.duplicate().mul(va);
                }
            }
        }

        Element hDivMul = h.duplicate().div(factorMul).getImmutable();
        Element x_invert = privateKeys.get(thisUserID).duplicate().invert().getImmutable();
        Element userSign = hDivMul.duplicate().powZn(x_invert).getImmutable();
        ringSignatures.set(thisUserID, userSign);

        long t1 = System.currentTimeMillis();
        long t_generation = t1-t0;

        System.out.println("Index of the user: "+thisUserID);
        System.out.println(ringSignatures);
        //System.out.println(validate());

        return t_generation;
    }

    public boolean validate(){
        Element h = G2.newElement();
        h.setFromHash(imgData,0,imgData.length);  //Hash the plaintext and map it to G2
        Element leftTerm = bp.pairing(g, h);
        Element rightTerm = bp.pairing(ringSignatures.get(0), publicKeys.get(0));
        for (int i = 1; i < groupSize; i++){
            Element tmp = bp.pairing(ringSignatures.get(i), publicKeys.get(i));
            rightTerm = rightTerm.duplicate().mul(tmp);
        }
        return leftTerm.isEqual(rightTerm);
    }

    private Object makeObj(final String item)  {
        return new Object() { public String toString() { return item; } };
    }

    public static void main(String[] args) {
        JFrame.setDefaultLookAndFeelDecorated(true);
        JFrame frame = new JFrame("SignAndUpload");
        SignAndUpload mInstance = new SignAndUpload();
        frame.setContentPane(mInstance.main);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        //JScrollPane scrollPane = new JScrollPane(mInstance.textPaneOutput);
        mInstance.comboBox1.addItem(mInstance.makeObj("0.1"));
        mInstance.comboBox1.addItem(mInstance.makeObj("0.2"));
        mInstance.comboBox1.addItem(mInstance.makeObj("0.3"));
        mInstance.comboBox1.addItem(mInstance.makeObj("0.4"));
        mInstance.comboBox1.addItem(mInstance.makeObj("0.5"));

        mInstance.thisUserID = new Random().nextInt(mInstance.numUser);

        for(int i = 0; i < mInstance.numUser; i++) {
            Element x = mInstance.Zr.newRandomElement().getImmutable();
            mInstance.privateKeys.add(x);
            Element v = mInstance.g.powZn(x).getImmutable();
            mInstance.publicKeys.add(v);
        }

        frame.pack();
        frame.setVisible(true);
    }
}
