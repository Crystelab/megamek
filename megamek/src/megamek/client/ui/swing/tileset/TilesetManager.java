/*
* MegaMek -
* Copyright (C) 2002, 2003, 2004 Ben Mazur (bmazur@sev.org)
* Copyright (C) 2018, 2020 The MegaMek Team
*
* This program is free software; you can redistribute it and/or modify it under
* the terms of the GNU General Public License as published by the Free Software
* Foundation; either version 2 of the License, or (at your option) any later
* version.
*
* This program is distributed in the hope that it will be useful, but WITHOUT
* ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
* FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
* details.
*/

package megamek.client.ui.swing.tileset;

import java.util.*;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Polygon;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageProducer;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import megamek.client.ui.ITilesetManager;
import megamek.client.ui.swing.GUIPreferences;
import megamek.client.ui.swing.boardview.BoardView1;
import megamek.client.ui.swing.tileset.MechTileset.MechEntry;
import megamek.client.ui.swing.util.EntityWreckHelper;
import megamek.client.ui.swing.util.ImageCache;
import megamek.client.ui.swing.util.ScaledImageFileFactory;
import megamek.client.ui.swing.util.PlayerColors;
import megamek.client.ui.swing.util.RotateFilter;
import megamek.common.*;
import megamek.common.logging.DefaultMmLogger;
import megamek.common.preference.*;
import megamek.common.util.fileUtils.DirectoryItems;
import megamek.common.util.ImageUtil;
import megamek.common.util.fileUtils.MegaMekFile;

/**
 * Handles loading and manipulating images from both the mech tileset and the
 * terrain tileset.
 *
 * @author Ben
 */
public class TilesetManager implements IPreferenceChangeListener, ITilesetManager {
    
    public static final String DIR_NAME_WRECKS = "wrecks"; //$NON-NLS-1$
    public static final String DIR_NAME_BOTTOM_DECALS = "bottomdecals";
    public static final String FILENAME_PREFIX_WRECKS = "destroyed_decal_";
    public static final String FILENAME_SUFFIX_WRECKS_ASSAULTPLUS = "assaultplus";
    public static final String FILENAME_SUFFIX_WRECKS_ULTRALIGHT = "ultralight";

    private static final int NUM_DECAL_ROTATIONS = 4;
    
    public static final String FILENAME_DEFAULT_HEX_SET = "defaulthexset.txt"; //$NON-NLS-1$

    private static final String FILENAME_NIGHT_IMAGE = new File("transparent", "night.png").toString();  //$NON-NLS-1$  //$NON-NLS-2$
    private static final String FILENAME_HEX_MASK = new File("transparent", "HexMask.png").toString();  //$NON-NLS-1$  //$NON-NLS-2$
    private static final String FILENAME_ARTILLERY_AUTOHIT_IMAGE = "artyauto.gif"; //$NON-NLS-1$
    private static final String FILENAME_ARTILLERY_ADJUSTED_IMAGE = "artyadj.gif"; //$NON-NLS-1$
    private static final String FILENAME_ARTILLERY_INCOMING_IMAGE = "artyinc.gif"; //$NON-NLS-1$

    public static final int ARTILLERY_AUTOHIT = 0;
    public static final int ARTILLERY_ADJUSTED = 1;
    public static final int ARTILLERY_INCOMING = 2;

    // component to load images to
    private BoardView1 boardview;

    // keep tracking of loading images
    private MediaTracker tracker;
    private boolean started = false;
    private boolean loaded = false;

    // keep track of camo images
    private DirectoryItems camos;

    // mech images
    private MechTileset mechTileset = new MechTileset(Configuration.unitImagesDir());
    private MechTileset wreckTileset = new MechTileset(
            new MegaMekFile(Configuration.unitImagesDir(), DIR_NAME_WRECKS).getFile());
    private List<EntityImage> mechImageList = new ArrayList<>();
    private Map<ArrayList<Integer>, EntityImage> mechImages = new HashMap<>();
    private Map<String, Image> wreckageDecals = new HashMap<>();
    private Map<String, Integer> wreckageDecalCount;

    // hex images
    private HexTileset hexTileset;

    private Image minefieldSign;
    private Image nightFog;

    /** An opaque hex shape used to limit draw operations to the exact hex shape. */
    private Image hexMask;

    private Image artilleryAutohit;
    private Image artilleryAdjusted;
    private Image artilleryIncoming;
    
    /**
     * Hexes under the effects of ECM have a shaded "static" image displayed,
     * to represent the noise generated by ECM.  This is a cache that stores
     * images for various colors (for Players, and possibly multiple players
     * in the same hex).
     */
    private Map<Color, Image> ecmStaticImages = new HashMap<>();
    
    /** Creates new TilesetManager. */
    public TilesetManager(BoardView1 bv) throws IOException {
        boardview = bv;
        hexTileset = new HexTileset(boardview.game);
        tracker = new MediaTracker(boardview);
        try {
            camos = new DirectoryItems(
                    Configuration.camoDir(),
                    "", //$NON-NLS-1$
                    ScaledImageFileFactory.getInstance()
            );
            
            String wreckDecalPath = String.format("%s/%s", DIR_NAME_WRECKS, DIR_NAME_BOTTOM_DECALS);
            File wreckDir = new File(Configuration.unitImagesDir(), wreckDecalPath);
            
            int bigWreckCount = wreckDir.list(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.startsWith(FILENAME_PREFIX_WRECKS) && 
                            name.contains(FILENAME_SUFFIX_WRECKS_ASSAULTPLUS) &&
                            name.endsWith(".png");
                }
            }).length;
            
            int tinyWreckCount = wreckDir.list(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.startsWith(FILENAME_PREFIX_WRECKS) && 
                            name.contains(FILENAME_SUFFIX_WRECKS_ULTRALIGHT) &&
                            name.endsWith(".png");
                }
            }).length;

            wreckageDecalCount = new HashMap<>();
            wreckageDecalCount.put(FILENAME_SUFFIX_WRECKS_ULTRALIGHT, tinyWreckCount);
            wreckageDecalCount.put(FILENAME_SUFFIX_WRECKS_ASSAULTPLUS, bigWreckCount);
            
        } catch (Exception e) {
            camos = null;
        }
        mechTileset.loadFromFile("mechset.txt"); //$NON-NLS-1$
        wreckTileset.loadFromFile("wreckset.txt"); //$NON-NLS-1$
        try {
            hexTileset.incDepth = 0;
            hexTileset.loadFromFile(PreferenceManager.getClientPreferences().getMapTileset());
        } catch (Exception FileNotFoundException) {
            DefaultMmLogger.getInstance().error(getClass(), "TilesetManager()", 
                    "Error loading tileset " + PreferenceManager.getClientPreferences().getMapTileset() +
                    " Reverting to default hexset! ");
            if (new MegaMekFile(Configuration.hexesDir(), FILENAME_DEFAULT_HEX_SET).getFile().exists()){
                hexTileset.loadFromFile(FILENAME_DEFAULT_HEX_SET);
            } else {
                DefaultMmLogger.getInstance().fatal(getClass(), "TilesetManager()", 
                        "Could not load default tileset " + FILENAME_DEFAULT_HEX_SET);
            }
        }
        PreferenceManager.getClientPreferences().addPreferenceChangeListener(this);
        GUIPreferences.getInstance().addPreferenceChangeListener(this);
    }

    /** React to changes in the settings. */
    public void preferenceChange(PreferenceChangeEvent e) {
        // A new Hex Tileset has been selected
        if (e.getName().equals(IClientPreferences.MAP_TILESET)) {
            HexTileset hts = new HexTileset(boardview.game);
            try {
                hexTileset.incDepth = 0;
                hts.loadFromFile((String) e.getNewValue());
                hexTileset = hts;
                boardview.clearHexImageCache();
            } catch (IOException ex) {
                return;
            }
        }
        
        // The setting to show damage decals and smoke has changed
        if (e.getName().equals(GUIPreferences.SHOW_DAMAGE_DECAL)) {
            reset();
        }
    }

    /** Retrieve an icon for the unit (used in the Unit Overview). */
    public Image iconFor(Entity entity) {
        EntityImage entityImage = getFromCache(entity, -1);
        if (entityImage == null) {
            DefaultMmLogger.getInstance().error(getClass(), "iconFor()", 
                    "Unable to load icon for entity: " + entity.getShortNameRaw());
            Image generic = getGenericImage(entity, -1, mechTileset);
            return (generic != null) ? ImageUtil.getScaledImage(generic, 56, 48) : null;
        }
        return entityImage.getIcon();
    }

    /** Retrieve a wreck icon for the unit. */
    public Image wreckMarkerFor(Entity entity, int secondaryPos) {
        EntityImage entityImage = getFromCache(entity, secondaryPos);
        if (entityImage == null) {
            DefaultMmLogger.getInstance().error(getClass(), "wreckMarkerFor()", 
                    "Unable to load wreck image for entity: " + entity.getShortNameRaw());
            return getGenericImage(entity, -1, wreckTileset);
        }
        return entityImage.getWreckFacing(entity.getFacing());
    }
    
    /** Retrieves the "devastated" decoration for the given entity */
    public Image getCraterFor(Entity entity, int secondaryPos) {
        Image marker = null;
        
        String suffix = EntityWreckHelper.getWeightSuffix(entity);
        String filename = String.format("crater_decal_%s.png", suffix);
        String path = String.format("%s/%s", DIR_NAME_WRECKS, DIR_NAME_BOTTOM_DECALS);
        
        if(wreckageDecals.containsKey(filename)) {
            marker = wreckageDecals.get(filename);
        } else {
            marker = TilesetManager.LoadSpecificImage(new File(Configuration.unitImagesDir(), path), filename);
            wreckageDecals.put(filename, marker);
        }
        
        return marker;
    }
    
    /** Retrieves the "destroyed" decoration for the given entity */
    public Image bottomLayerWreckMarkerFor(Entity entity, int secondaryPos) {
        Image marker = null;

        // wreck filenames are in the format destroyed_decal_x_weightsuffix, where x is 1 through however many bottom splats we have
        // in the directory. To make sure we don't swap splats between entities, we make it depend on entity ID        
        String suffix = EntityWreckHelper.getWeightSuffix(entity);
        int wreckNum = (entity.getId() % this.wreckageDecalCount.get(suffix)) + 1;
        String filename = String.format("%s%d_%s.png", FILENAME_PREFIX_WRECKS, wreckNum, suffix);
        String path = String.format("%s/%s", DIR_NAME_WRECKS, DIR_NAME_BOTTOM_DECALS);
        
        if(wreckageDecals.containsKey(filename)) {
            marker = wreckageDecals.get(filename);
        } else {
            marker = TilesetManager.LoadSpecificImage(new File(Configuration.unitImagesDir(), path), filename);
            wreckageDecals.put(filename, marker);
        }
        
        return marker;
    }
    
    /** Retrieves the "destroyed" decoration for the given entity */
    public Image bottomLayerFuelLeakMarkerFor(Entity entity) {
        Image marker = null;
        
        String suffix = EntityWreckHelper.getWeightSuffix(entity);
        String filename = String.format("fuelleak_decal_%s.png", suffix);
        String path = String.format("%s/%s", DIR_NAME_WRECKS, DIR_NAME_BOTTOM_DECALS);
        
        int rotationKey = entity.getId() % NUM_DECAL_ROTATIONS;
        String imageKey = String.format("%s%s", filename, rotationKey);
        
        if(!wreckageDecals.containsKey(imageKey)) {
            Image baseImage = TilesetManager.LoadSpecificImage(new File(Configuration.unitImagesDir(), path), filename);
            
            for(double x = 0; x < NUM_DECAL_ROTATIONS; x++) {
                RotateFilter rf = new RotateFilter(x * 90);
                String newImageKey = String.format("%s%s", filename, (int) x);
                
                ImageProducer ip = new FilteredImageSource(baseImage.getSource(), rf);
                Image resultImage = Toolkit.getDefaultToolkit().createImage(ip);
                wreckageDecals.put(newImageKey, resultImage);
            }
        }
        
        marker = wreckageDecals.get(imageKey);
        
        return marker;
    }
    
    /** Retrieves the "destroyed" decoration for the given entity */
    public Image bottomLayerMotiveMarkerFor(Entity entity) {
        Image marker = null;
        
        String weightSuffix = EntityWreckHelper.getWeightSuffix(entity);
        String motivePrefix = EntityWreckHelper.getMotivePrefix(entity);
        
        if(motivePrefix != null) {
            String filename = String.format("%s_decal_%s.png", motivePrefix, weightSuffix);
            String path = String.format("%s/%s", DIR_NAME_WRECKS, DIR_NAME_BOTTOM_DECALS);
            
            int rotationKey = entity.getId() % NUM_DECAL_ROTATIONS;
            String imageKey = String.format("%s%s", filename, rotationKey);
            
            if(!wreckageDecals.containsKey(imageKey)) {
                Image baseImage = TilesetManager.LoadSpecificImage(new File(Configuration.unitImagesDir(), path), filename);
                
                for(double x = 0; x < NUM_DECAL_ROTATIONS; x++) {
                    RotateFilter rf = new RotateFilter(x * 90);
                    String newImageKey = String.format("%s%s", filename, (int) x);
                    
                    ImageProducer ip = new FilteredImageSource(baseImage.getSource(), rf);
                    Image resultImage = Toolkit.getDefaultToolkit().createImage(ip);
                    wreckageDecals.put(newImageKey, resultImage);
                }
            }
            
            marker = wreckageDecals.get(imageKey);
        }
        
        return marker;
    }

    /** Retrieve an image for the unit. */
    public Image imageFor(Entity entity) {
        return imageFor(entity, -1);
    }

    /** Retrieve an image for the unit. */
    public Image imageFor(Entity entity, int secondaryPos) {
        // mechs look like they're facing their secondary facing
        // (except QuadVees, which are using turrets instead of torso twists
        if (((entity instanceof Mech) || (entity instanceof Protomech))
                && !(entity instanceof QuadVee)) {
            return imageFor(entity, entity.getSecondaryFacing(), secondaryPos);
        }
        return imageFor(entity, entity.getFacing(), secondaryPos);
    }

    /** Retrieve an image for the unit. */
    public Image imageFor(Entity entity, int facing, int secondaryPos) {
        EntityImage entityImage = getFromCache(entity, secondaryPos);
        if (entityImage == null) {
            DefaultMmLogger.getInstance().error(getClass(), "imageFor()", 
                    "Unable to load image for entity: " + entity.getShortNameRaw());
            return getGenericImage(entity, -1, mechTileset);
        }
        // get image rotated for facing
        return entityImage.getFacing(facing);
    }
    
    
    /** Retrieves the image from the cache and loads it if not present. */
    private EntityImage getFromCache(Entity entity, int secondaryPos) {
        ArrayList<Integer> temp = new ArrayList<Integer>();
        temp.add(entity.getId());
        temp.add(secondaryPos);
        EntityImage result = mechImages.get(temp);
        
        // Image could be null, for example with double blind
        if (result == null) {
            DefaultMmLogger.getInstance().info(getClass(), "getFromCache()", 
                    "Loading image on the fly: " + entity.getShortNameRaw());
            loadImage(entity, secondaryPos);
            result = mechImages.get(temp);
        }
        return result;
    }
    
    
    /** Retrieves a generic unit image if possible. May still return null! */
    private Image getGenericImage(Entity entity, int secondaryPos, MechTileset tileSet) {
        MechEntry defaultEntry = tileSet.genericFor(entity, secondaryPos);
        if (defaultEntry.getImage() == null) {
            defaultEntry.loadImage(boardview);
        }
        return defaultEntry.getImage();
    }

    /**
     * Return the base image for the hex
     */
    public Image baseFor(IHex hex) {
        return hexTileset.getBase(hex, boardview);
    }

    /**
     * Return a list of superimposed images for the hex
     */
    public List<Image> supersFor(IHex hex) {
        return hexTileset.getSupers(hex, boardview);
    }

    /**
     * Return a list of orthographic images for the hex
     */
    public List<Image> orthoFor(IHex hex) {
        return hexTileset.getOrtho(hex, boardview);
    }

    public Image getMinefieldSign() {
        return minefieldSign;
    }

    public Image getNightFog() {
        return nightFog;
    }

    public Image getHexMask() {
        return hexMask;
    }

    public Set<String> getThemes() {
        return hexTileset.getThemes();
    }

    /**
     * Hexes affected by ECM will have a shaded static effect drawn on them.
     * This method will check the cache for a suitable static image for a given
     * color, and if one doesn't exists an image is created and cached.
     *
     * @param tint
     * @return
     */
    public Image getEcmStaticImage(Color tint) {
        Image image = ecmStaticImages.get(tint);
        if (image == null) {
            // Create a new hex-sized image
            image = new BufferedImage(HexTileset.HEX_W,
                    HexTileset.HEX_H, BufferedImage.TYPE_INT_ARGB);
            Graphics g = image.getGraphics();
            Polygon hexPoly = boardview.getHexPoly();
            g.setColor(tint.darker());
            // Draw ~200 small "ovals" at random locations within a a hex
            // A 3x3 oval ends up looking more like a cross
            for (int i = 0; i < 200; i++) {
                int x = (int)(Math.random() * HexTileset.HEX_W);
                int y = (int)(Math.random() * HexTileset.HEX_H);
                if (hexPoly.contains(x,y)) {
                    g.fillOval(x, y, 3, 3);
                }
            }
            ecmStaticImages.put(tint, image);
        }
        return image;
    }

    public Image getArtilleryTarget(int which) {
        switch (which) {
            case ARTILLERY_AUTOHIT:
                return artilleryAutohit;
            case ARTILLERY_ADJUSTED:
                return artilleryAdjusted;
            case ARTILLERY_INCOMING:
            default:
                return artilleryIncoming;
        }
    }

    /**
     * @return true if we're in the process of loading some images
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * @return true if we're done loading images
     */
    public synchronized boolean isLoaded() {
        if (!loaded) {
            loaded = tracker.checkAll(true);
        }
        return started && loaded;
    }

    /**
     * Load all the images we'll need for the game and place them in the tracker
     */
    public void loadNeededImages(IGame game) {
        loaded = false;
        IBoard board = game.getBoard();
        // pre-match all hexes with images, load hex images
        int width = board.getWidth();
        int height = board.getHeight();
        // We want to cache as many of the images as we can, but if we have
        // more images than cache size, lets not waste time
        if ((width*height) > ImageCache.MAX_SIZE){
            // Find the largest size by size square we can fit in the cache
            int max_dim = (int)Math.sqrt(ImageCache.MAX_SIZE);
            if (width < max_dim) {
        	        height = (int)(ImageCache.MAX_SIZE / width);
            } else if (height < max_dim) {
        	        width = (int)(ImageCache.MAX_SIZE / height);
            } else {
                width = height = max_dim;
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                IHex hex = board.getHex(x, y);
                loadHexImage(hex);
            }
        }

        // load all mech images
        for (Entity e : game.getEntitiesVector()) {
            if (e.getSecondaryPositions().isEmpty()) {
                loadImage(e, -1);
            } else {
                for (Integer secPos : e.getSecondaryPositions().keySet()) {
                    loadImage(e, secPos);
                }
            }

        }

        minefieldSign = LoadSpecificImage(Configuration.hexesDir(), Minefield.FILENAME_IMAGE);
        nightFog = LoadSpecificImage(Configuration.hexesDir(), FILENAME_NIGHT_IMAGE);
        hexMask = LoadSpecificImage(Configuration.hexesDir(), FILENAME_HEX_MASK);
        
        artilleryAutohit = LoadSpecificImage(Configuration.hexesDir(), FILENAME_ARTILLERY_AUTOHIT_IMAGE);
        artilleryAdjusted = LoadSpecificImage(Configuration.hexesDir(), FILENAME_ARTILLERY_ADJUSTED_IMAGE);
        artilleryIncoming = LoadSpecificImage(Configuration.hexesDir(), FILENAME_ARTILLERY_INCOMING_IMAGE);
        
        started = true;
    }
    
    /** Local method. Loads and returns the image. */ 
    public static Image LoadSpecificImage(File path, String name) {
        Image result = ImageUtil.loadImageFromFile(
                new MegaMekFile(path, name).toString());
        if (result.getWidth(null) <= 0 || result.getHeight(null) <= 0) {
            DefaultMmLogger.getInstance().error(TilesetManager.class, "LoadImage()", 
                    "Error opening image: " + name);
        }
        return result;
    }
    
    public synchronized void reloadImage(Entity en) {
        if (en.getSecondaryPositions().isEmpty()) {
            loadImage(en, -1);
        } else {
            en.getSecondaryPositions().keySet().forEach(p -> loadImage(en, p));
        }
    }

    /**
     * Loads the image(s) for this hex into the tracker.
     *
     * @param hex the hex to load
     */
    private synchronized void loadHexImage(IHex hex) {
        hexTileset.assignMatch(hex, boardview);
        hexTileset.trackHexImages(hex, tracker);
    }

    /**
     * Removes the hex images from the cache.
     *
     * @param hex
     */
    public void clearHex(IHex hex) {
        hexTileset.clearHex(hex);
    }

    /**
     * Waits until a certain hex's images are done loading.
     *
     * @param hex the hex to wait for
     */
    public synchronized void waitForHex(IHex hex) {
        loadHexImage(hex);
        try {
            tracker.waitForID(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads all the hex tileset images
     */
    public synchronized void loadAllHexes() {
        hexTileset.loadAllImages(boardview, tracker);
    }

    /**
     *  Loads a preview image of the unit into the BufferedPanel.
     */
    public Image loadPreviewImage(Entity entity, Image camo, int tint, Component bp) {
        Image base = mechTileset.imageFor(entity, boardview, -1);
        EntityImage entityImage = new EntityImage(base, tint, camo, bp, entity);
        entityImage.loadFacings();
        Image preview = entityImage.getFacing(entity.getFacing());

        MediaTracker loadTracker = new MediaTracker(boardview);
        loadTracker.addImage(preview, 0);
        try {
            loadTracker.waitForID(0);
        } catch (InterruptedException e) {
            // should never come here

        }

        return preview;
    }

    /**
     * Returns the camo pattern for the given player 
     * or null, if the player has no camo or there was an error.
     */
    public Image getPlayerCamo(IPlayer player) {
        return getCamo(player.getCamoCategory(), player.getCamoFileName());
    }

    /**
     * Returns the camo pattern for the given entity 
     * or null, if the player has no camo or there was an error.
     */
    public Image getEntityCamo(Entity entity) {
        return getCamo(entity.getCamoCategory(), entity.getCamoFileName());
    }

    /** Returns the camo pattern, if possible or null. */
    private Image getCamo(String category, String name) {
        // Return a null if no camo
        if ((category == null) || category.equals(IPlayer.NO_CAMO)) {
            return null;
        }

        // Try to get the camo file.
        Image camo = null;
        try {
            // Translate the root camo directory name.
            if (IPlayer.ROOT_CAMO.equals(category)) {
                category = ""; //$NON-NLS-1$
            }
            camo = (Image) camos.getItem(category, name);
        } catch (Exception err) {
            err.printStackTrace();
        }
        return camo;
    }
    
    /**
     * Load a single entity image
     */
    public synchronized void loadImage(Entity entity, int secondaryPos) {
        Image base = mechTileset.imageFor(entity, boardview, secondaryPos);
        Image wreck = wreckTileset.imageFor(entity, boardview, secondaryPos);

        IPlayer player = entity.getOwner();
        int tint = PlayerColors.getColorRGB(player.getColorIndex());

        Image camo = null;
        if (getEntityCamo(entity) != null) {
            camo = getEntityCamo(entity);
        } else {
            camo = getPlayerCamo(player);
        }
        EntityImage entityImage = null;

        // check if we have a duplicate image already loaded
        for (Iterator<EntityImage> j = mechImageList.iterator(); j.hasNext();) {
            EntityImage onList = j.next();
            if ((onList.getBase() != null) && onList.getBase().equals(base)
                    && (onList.tint == tint) && (onList.getCamo() != null)
                    && onList.getCamo().equals(camo) && onList.getDmgLvl() == entity.getDamageLevel(false)) {
                entityImage = onList;
                break;
            }
        }

        // if we don't have a cached image, make a new one
        if (entityImage == null) {
            entityImage = new EntityImage(base, wreck, tint, camo, boardview, entity, secondaryPos);
            mechImageList.add(entityImage);
            entityImage.loadFacings();
            for (int j = 0; j < 6; j++) {
                tracker.addImage(entityImage.getFacing(j), 1);
            }
        }

        // relate this id to this image set
        ArrayList<Integer> temp = new ArrayList<Integer>();
        temp.add(entity.getId());
        temp.add(secondaryPos);
        mechImages.put(temp, entityImage);
    }

    /**
     * Resets the started and loaded flags
     */
    public synchronized void reset() {
        loaded = false;
        started = false;

        tracker = new MediaTracker(boardview);
        mechImageList.clear();
        mechImages.clear();
        hexTileset.clearAllHexes();
    }    
}
