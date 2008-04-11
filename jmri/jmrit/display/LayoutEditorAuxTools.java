// jmri.jmrit.display.LayoutEditorAuxTools.java

package jmri.jmrit.display;

import jmri.InstanceManager;
import jmri.util.JmriJFrame;
import jmri.Conditional;
import jmri.Logix;

import java.awt.*;
import java.awt.geom.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.util.ResourceBundle;
import java.util.ArrayList;

import javax.swing.*;

import jmri.Sensor;
import jmri.Turnout;
import jmri.SignalHead;
import jmri.Path;
import jmri.BeanSetting;
import jmri.jmrit.catalog.NamedIcon;
import jmri.jmrit.blockboss.BlockBossLogic;

/**
 * LayoutEditorAuxTools provides tools making use of layout connectivity available 
 *	in Layout Editor panels.  (More tools are in LayoutEditorTools.java.)
 * <P>
 * This module manages block connectivity for its associated LayoutEditor.
 * <P>
 * The tools in this module are accessed via the Tools menu in Layout Editor, or 
 *	directly from LayoutEditor or LayoutEditor specific modules.
 * <P>
 * @author Dave Duchamp Copyright (c) 2008
 * @version $Revision: 1.2 $
 */

public class LayoutEditorAuxTools 
{

	// Defined text resource
	ResourceBundle rb = ResourceBundle.getBundle("jmri.jmrit.display.LayoutEditorBundle");
	
	// constants
	
	// operational instance variables 
	private LayoutEditor layoutEditor = null;
	private ArrayList cList = null; //LayoutConnectivity list
	private boolean blockConnectivityChanged = false;  // true if block connectivity may have changed
	private boolean initialized = false;
	
	// constructor method
	public LayoutEditorAuxTools(LayoutEditor thePanel) {
		layoutEditor = thePanel;
	}

	// register a change in block connectivity that may require an update of connectivity list
	public void setBlockConnectivityChanged() {blockConnectivityChanged = true;}
//	public void resetBlockConnectivityChanged() {blockConnectivityChanged = false;}
	
	/**
	 * Get Connectivity involving a specific Layout Block
	 * <P>
	 * This routine returns an ArrayList of BlockConnectivity objects involving the specified
	 *		LayoutBlock.
	 */
	public ArrayList getConnectivityList(LayoutBlock blk) {
		if (blockConnectivityChanged) {
			updateBlockConnectivity();
		}	
		ArrayList retList = new ArrayList();
		for (int i = 0;i<cList.size();i++) {
			LayoutConnectivity lc = (LayoutConnectivity)cList.get(i);
			if ( (lc.getBlock1() == blk) || (lc.getBlock2() == blk) ) {
				retList.add((Object)lc);
			}
		}
		return (retList);
	}
	
	/**
	 * Initializes the block connectivity (block boundaries) for a Layout Editor panel.
	 * <P>
	 * This routine sets up the LayoutConnectivity objects needed to show the current 
	 *		connectivity. It gets its information from arrays contained in LayoutEditor.
	 * <P>
	 * One LayoutConnectivity object is created for each block boundary -- connection 
	 *		points where two blocks join. Block boundaries can occur whereever a track segment 
	 *		in one block joins with:
	 *		1) a track segment in another block -OR-
	 *		2) a connection point in a layout turnout in another block -OR-
	 *      3) a connection point in a level crossing in another block.
	 * <P>
	 * The first block is always a track segment.  The direction set in the LayoutConnectivity 
	 *		is the direction of the track segment alone for cases 2) and 3) above.  For case 1),
	 *		two track segments, the direction reflects an "average" over the two track segments.
	 *		See LayoutConnectivity for the allowed values of direction.
	 * <P>
	 *
	 */
	public void initializeBlockConnectivity () {
		if (initialized) {
			log.error("Call to initialize a connectivity list that has already been initialized");
			return;
		}
		cList = new ArrayList();
		LayoutBlock blk1 = null;
		LayoutBlock blk2 = null;
		LayoutConnectivity c = null;
		Point2D p1;
		Point2D p2;
		// Check for block boundaries at positionable points.
		if (layoutEditor.pointList.size()>0) {
			PositionablePoint p = null;
			TrackSegment ts1 = null;
			TrackSegment ts2 = null;
			for (int i = 0; i<layoutEditor.pointList.size(); i++) {
				p = (PositionablePoint)layoutEditor.pointList.get(i);
				if (p.getType() == PositionablePoint.ANCHOR) {
					// within PositionablePoints, only ANCHOR points can be block boundaries
					ts1 = p.getConnect1();
					ts2 = p.getConnect2();
					if ( (ts1!=null) && (ts2!=null) ) {
						blk1 = ts1.getLayoutBlock();
						blk2 = ts2.getLayoutBlock();
						if ( (blk1!=null) && (blk2!=null) && (blk1!=blk2) ) {
							// this is a block boundary, create a LayoutConnectivity
							c = new LayoutConnectivity(blk1,blk2);
							// determine direction from block 1 to block 2
							if (ts1.getConnect1()==p) p1 = layoutEditor.getCoords(
																ts1.getConnect2(),ts1.getType2());
							else p1 = layoutEditor.getCoords(ts1.getConnect1(),ts1.getType1());
							if (ts2.getConnect1()==p) p2 = layoutEditor.getCoords(
																ts2.getConnect2(),ts2.getType2());
							else p2 = layoutEditor.getCoords(ts2.getConnect1(),ts2.getType1());
							c.setDirection(computeDirection(p1,p2));
							// save Connections
							c.setConnections(ts1,(Object)ts2,LayoutEditor.TRACK);
							// add to list
							cList.add(c);
						}
					}
				}
			}
		}
		// Check for block boundaries at layout turnouts and level crossings
		if (layoutEditor.trackList.size()>0) {
			LayoutTurnout lt = null;
			int type = 0;
			LevelXing lx = null;
			TrackSegment ts = null;
			for (int i = 0; i<layoutEditor.trackList.size(); i++) {
				ts = (TrackSegment)layoutEditor.trackList.get(i);
				// ensure that block is assigned
				blk1 = ts.getLayoutBlock();
				if (blk1!=null) {
					// check first connection for turnout or level crossing
					if ( (ts.getType1()>=LayoutEditor.TURNOUT_A) &&
												(ts.getType1()<=LayoutEditor.LEVEL_XING_D) ) {
						// have connection to turnout or level crossing							
						if (ts.getType1()<=LayoutEditor.TURNOUT_D) {
							// have connection to a turnout, is block different
							lt = (LayoutTurnout)ts.getConnect1();
							type = ts.getType1();
							blk2 = lt.getLayoutBlock();
							if (lt.getTurnoutType()>LayoutTurnout.WYE_TURNOUT) {
								// not RH, LH, or WYE turnout - other blocks possible
								if ( (type==LayoutEditor.TURNOUT_B) && (lt.getLayoutBlockB()!=null) )
									blk2 = lt.getLayoutBlockB();
								if ( (type==LayoutEditor.TURNOUT_C) && (lt.getLayoutBlockC()!=null) )
									blk2 = lt.getLayoutBlockC();
								if ( (type==LayoutEditor.TURNOUT_D) && (lt.getLayoutBlockD()!=null) )
									blk2 = lt.getLayoutBlockD();
							}
							if ((blk2!=null) && (blk1!=blk2)) {
								// have a block boundary, create a LayoutConnectivity
								c = new LayoutConnectivity(blk1,blk2);
								c.setConnections(ts,(Object)lt,type);
								c.setDirection(computeDirection(layoutEditor.getCoords(ts.getConnect2(),
										ts.getType2()),layoutEditor.getCoords(ts.getConnect1(),type)));
								// add to list
								cList.add(c);
							}
						}
						else {
							// have connection to a level crossing
							lx = (LevelXing)ts.getConnect1();
							type = ts.getType1();
							if ( (type==LayoutEditor.LEVEL_XING_A) || (type==LayoutEditor.LEVEL_XING_C) ) {
								blk2 = lx.getLayoutBlockAC();
							}
							else {
								blk2 = lx.getLayoutBlockBD();
							}
							if ((blk2!=null) && (blk1!=blk2)) {
								// have a block boundary, create a LayoutConnectivity
								c = new LayoutConnectivity(blk1,blk2);
								c.setConnections(ts,(Object)lx,type);
								c.setDirection(computeDirection(layoutEditor.getCoords(ts.getConnect2(),
										ts.getType2()),layoutEditor.getCoords(ts.getConnect1(),type)));
								// add to list
								cList.add(c);
							}
						}
					}
					// check second connection for turnout or level crossing
					if ( (ts.getType2()>=LayoutEditor.TURNOUT_A) &&
												(ts.getType2()<=LayoutEditor.LEVEL_XING_D) ) {
						// have connection to turnout or level crossing							
						if (ts.getType2()<=LayoutEditor.TURNOUT_D) {
							// have connection to a turnout
							lt = (LayoutTurnout)ts.getConnect2();
							type = ts.getType2();
							blk2 = lt.getLayoutBlock();
							if (lt.getTurnoutType()>LayoutTurnout.WYE_TURNOUT) {
								// not RH, LH, or WYE turnout - other blocks possible
								if ( (type==LayoutEditor.TURNOUT_B) && (lt.getLayoutBlockB()!=null) )
									blk2 = lt.getLayoutBlockB();
								if ( (type==LayoutEditor.TURNOUT_C) && (lt.getLayoutBlockC()!=null) )
									blk2 = lt.getLayoutBlockC();
								if ( (type==LayoutEditor.TURNOUT_D) && (lt.getLayoutBlockD()!=null) )
									blk2 = lt.getLayoutBlockD();
							}
							if ((blk2!=null) && (blk1!=blk2)) {
								// have a block boundary, create a LayoutConnectivity
								c = new LayoutConnectivity(blk1,blk2);
								c.setConnections(ts,(Object)lt,type);
								c.setDirection(computeDirection(layoutEditor.getCoords(ts.getConnect1(),
										ts.getType1()),layoutEditor.getCoords(ts.getConnect2(),type)));
								// add to list
								cList.add(c);
							}
						}
						else {
							// have connection to a level crossing
							lx = (LevelXing)ts.getConnect2();
							type = ts.getType2();
							if ( (type==LayoutEditor.LEVEL_XING_A) || (type==LayoutEditor.LEVEL_XING_C) ) {
								blk2 = lx.getLayoutBlockAC();
							}
							else {
								blk2 = lx.getLayoutBlockBD();
							}
							if ((blk2!=null) && (blk1!=blk2)) {
								// have a block boundary, create a LayoutConnectivity
								c = new LayoutConnectivity(blk1,blk2);
								c.setConnections(ts,(Object)lx,type);
								c.setDirection(computeDirection(layoutEditor.getCoords(ts.getConnect1(),
										ts.getType1()),layoutEditor.getCoords(ts.getConnect2(),type)));
								// add to list
								cList.add(c);
							}
						}
					}
				}
			}
		}
		// check for block boundaries internal to crossover turnouts
		if (layoutEditor.turnoutList.size()>0) {
			LayoutTurnout lt = null;
			for (int i = 0;i<layoutEditor.turnoutList.size();i++) {
				lt = (LayoutTurnout)layoutEditor.turnoutList.get(i);
				// check for layout turnout
				if ( (lt.getTurnoutType()>=LayoutTurnout.DOUBLE_XOVER) && 
						(lt.getLayoutBlock()!=null) ) {
					// have a crossover turnout with at least one block, check for multiple blocks
					if ( (lt.getLayoutBlockB()!=null) || (lt.getLayoutBlockC()!=null) ||
									(lt.getLayoutBlockD()!=null) ) {
						// have multiple blocks and therefore internal block boundaries
						if ((lt.getLayoutBlockB()!=null) && (lt.getLayoutBlock()!=lt.getLayoutBlockB())) {
							// have a AB block boundary, create a LayoutConnectivity
							c = new LayoutConnectivity(lt.getLayoutBlock(),lt.getLayoutBlockB());
							c.setXoverBoundary(lt,LayoutConnectivity.XOVER_BOUNDARY_AB);
							c.setDirection(computeDirection(lt.getCoordsA(),lt.getCoordsB()));
							cList.add(c);
						}
						if ((lt.getTurnoutType()!=LayoutTurnout.LH_XOVER) && (lt.getLayoutBlockC()!=null) && 
													(lt.getLayoutBlock()!=lt.getLayoutBlockC())) {
							// have a AC block boundary, create a LayoutConnectivity
							c = new LayoutConnectivity(lt.getLayoutBlock(),lt.getLayoutBlockC());
							c.setXoverBoundary(lt,LayoutConnectivity.XOVER_BOUNDARY_AC);
							c.setDirection(computeDirection(lt.getCoordsA(),lt.getCoordsC()));
							cList.add(c);
						}
						if ((lt.getLayoutBlockC()!=null) && (lt.getLayoutBlockD()!=null) && 
													(lt.getLayoutBlockC()!=lt.getLayoutBlockD())) {
							// have a CD block boundary, create a LayoutConnectivity
							c = new LayoutConnectivity(lt.getLayoutBlockC(),lt.getLayoutBlockD());
							c.setXoverBoundary(lt,LayoutConnectivity.XOVER_BOUNDARY_CD);
							c.setDirection(computeDirection(lt.getCoordsC(),lt.getCoordsD()));
							cList.add(c);
						}
						if ((lt.getTurnoutType()!=LayoutTurnout.RH_XOVER) && (lt.getLayoutBlockB()!=null) && 
								(lt.getLayoutBlockD()!=null) && (lt.getLayoutBlockB()!=lt.getLayoutBlockD())) {
							// have a BD block boundary, create a LayoutConnectivity
							c = new LayoutConnectivity(lt.getLayoutBlockB(),lt.getLayoutBlockD());
							c.setXoverBoundary(lt,LayoutConnectivity.XOVER_BOUNDARY_BD);
							c.setDirection(computeDirection(lt.getCoordsB(),lt.getCoordsD()));
							cList.add(c);
						}
					}
				}
			}		
		}
		initialized = true;
	}	
		
	/**
	 * Updates the block connectivity (block boundaries) for a Layout Editor panel after changes may have
	 *		been made.
	 */
	private void updateBlockConnectivity() {
		int sz = cList.size();
		boolean[] found = new boolean[sz];
		for (int i=0;i<sz;i++) {found[i]=false;}
		LayoutBlock blk1 = null;
		LayoutBlock blk2 = null;
		LayoutConnectivity c = null;
		Point2D p1;
		Point2D p2;
		// Check for block boundaries at positionable points.
		if (layoutEditor.pointList.size()>0) {
			PositionablePoint p = null;
			TrackSegment ts1 = null;
			TrackSegment ts2 = null;
			for (int i = 0; i<layoutEditor.pointList.size(); i++) {
				p = (PositionablePoint)layoutEditor.pointList.get(i);
				if (p.getType() == PositionablePoint.ANCHOR) {
					// within PositionablePoints, only ANCHOR points can be block boundaries
					ts1 = p.getConnect1();
					ts2 = p.getConnect2();
					if ( (ts1!=null) && (ts2!=null) ) {
						blk1 = ts1.getLayoutBlock();
						blk2 = ts2.getLayoutBlock();
						if ( (blk1!=null) && (blk2!=null) && (blk1!=blk2) ) {
							// this is a block boundary, create a LayoutConnectivity
							c = new LayoutConnectivity(blk1,blk2);
							// determine direction from block 1 to block 2
							if (ts1.getConnect1()==p) p1 = layoutEditor.getCoords(
																ts1.getConnect2(),ts1.getType2());
							else p1 = layoutEditor.getCoords(ts1.getConnect1(),ts1.getType1());
							if (ts2.getConnect1()==p) p2 = layoutEditor.getCoords(
																ts2.getConnect2(),ts2.getType2());
							else p2 = layoutEditor.getCoords(ts2.getConnect1(),ts2.getType1());
							c.setDirection(computeDirection(p1,p2));
							// save Connections
							c.setConnections(ts1,(Object)ts2,LayoutEditor.TRACK);
							// add to list, if not already present
							checkConnectivity(c,found);
						}
					}
				}
			}
		}
		// Check for block boundaries at layout turnouts and level crossings
		if (layoutEditor.trackList.size()>0) {
			LayoutTurnout lt = null;
			int type = 0;
			LevelXing lx = null;
			TrackSegment ts = null;
			for (int i = 0; i<layoutEditor.trackList.size(); i++) {
				ts = (TrackSegment)layoutEditor.trackList.get(i);
				// ensure that block is assigned
				blk1 = ts.getLayoutBlock();
				if (blk1!=null) {
					// check first connection for turnout or level crossing
					if ( (ts.getType1()>=LayoutEditor.TURNOUT_A) &&
												(ts.getType1()<=LayoutEditor.LEVEL_XING_D) ) {
						// have connection to turnout or level crossing							
						if (ts.getType1()<=LayoutEditor.TURNOUT_D) {
							// have connection to a turnout, is block different
							lt = (LayoutTurnout)ts.getConnect1();
							type = ts.getType1();
							blk2 = lt.getLayoutBlock();
							if (lt.getTurnoutType()>LayoutTurnout.WYE_TURNOUT) {
								// not RH, LH, or WYE turnout - other blocks possible
								if ( (type==LayoutEditor.TURNOUT_B) && (lt.getLayoutBlockB()!=null) )
									blk2 = lt.getLayoutBlockB();
								if ( (type==LayoutEditor.TURNOUT_C) && (lt.getLayoutBlockC()!=null) )
									blk2 = lt.getLayoutBlockC();
								if ( (type==LayoutEditor.TURNOUT_D) && (lt.getLayoutBlockD()!=null) )
									blk2 = lt.getLayoutBlockD();
							}
							if ((blk2!=null) && (blk1!=blk2)) {
								// have a block boundary, create a LayoutConnectivity
								c = new LayoutConnectivity(blk1,blk2);
								c.setConnections(ts,(Object)lt,type);
								c.setDirection(computeDirection(layoutEditor.getCoords(ts.getConnect2(),
										ts.getType2()),layoutEditor.getCoords(ts.getConnect1(),type)));
								// add to list
								checkConnectivity(c,found);
							}
						}
						else {
							// have connection to a level crossing
							lx = (LevelXing)ts.getConnect1();
							type = ts.getType1();
							if ( (type==LayoutEditor.LEVEL_XING_A) || (type==LayoutEditor.LEVEL_XING_C) ) {
								blk2 = lx.getLayoutBlockAC();
							}
							else {
								blk2 = lx.getLayoutBlockBD();
							}
							if ((blk2!=null) && (blk1!=blk2)) {
								// have a block boundary, create a LayoutConnectivity
								c = new LayoutConnectivity(blk1,blk2);
								c.setConnections(ts,(Object)lx,type);
								c.setDirection(computeDirection(layoutEditor.getCoords(ts.getConnect2(),
										ts.getType2()),layoutEditor.getCoords(ts.getConnect1(),type)));
								// add to list
								checkConnectivity(c,found);
							}
						}
					}
					// check second connection for turnout or level crossing
					if ( (ts.getType2()>=LayoutEditor.TURNOUT_A) &&
												(ts.getType2()<=LayoutEditor.LEVEL_XING_D) ) {
						// have connection to turnout or level crossing							
						if (ts.getType2()<=LayoutEditor.TURNOUT_D) {
							// have connection to a turnout
							lt = (LayoutTurnout)ts.getConnect2();
							type = ts.getType2();
							blk2 = lt.getLayoutBlock();
							if (lt.getTurnoutType()>LayoutTurnout.WYE_TURNOUT) {
								// not RH, LH, or WYE turnout - other blocks possible
								if ( (type==LayoutEditor.TURNOUT_B) && (lt.getLayoutBlockB()!=null) )
									blk2 = lt.getLayoutBlockB();
								if ( (type==LayoutEditor.TURNOUT_C) && (lt.getLayoutBlockC()!=null) )
									blk2 = lt.getLayoutBlockC();
								if ( (type==LayoutEditor.TURNOUT_D) && (lt.getLayoutBlockD()!=null) )
									blk2 = lt.getLayoutBlockD();
							}
							if ((blk2!=null) && (blk1!=blk2)) {
								// have a block boundary, create a LayoutConnectivity
								c = new LayoutConnectivity(blk1,blk2);
								c.setConnections(ts,(Object)lt,type);
								c.setDirection(computeDirection(layoutEditor.getCoords(ts.getConnect1(),
										ts.getType1()),layoutEditor.getCoords(ts.getConnect2(),type)));
								// add to list
								checkConnectivity(c,found);
							}
						}
						else {
							// have connection to a level crossing
							lx = (LevelXing)ts.getConnect2();
							type = ts.getType2();
							if ( (type==LayoutEditor.LEVEL_XING_A) || (type==LayoutEditor.LEVEL_XING_C) ) {
								blk2 = lx.getLayoutBlockAC();
							}
							else {
								blk2 = lx.getLayoutBlockBD();
							}
							if ((blk2!=null) && (blk1!=blk2)) {
								// have a block boundary, create a LayoutConnectivity
								c = new LayoutConnectivity(blk1,blk2);
								c.setConnections(ts,(Object)lx,type);
								c.setDirection(computeDirection(layoutEditor.getCoords(ts.getConnect1(),
										ts.getType1()),layoutEditor.getCoords(ts.getConnect2(),type)));
								// add to list
								checkConnectivity(c,found);
							}
						}
					}
				}
			}
		}
		// check for block boundaries internal to crossover turnouts
		if (layoutEditor.turnoutList.size()>0) {
			LayoutTurnout lt = null;
			for (int i = 0;i<layoutEditor.turnoutList.size();i++) {
				lt = (LayoutTurnout)layoutEditor.turnoutList.get(i);
				// check for layout turnout
				if ( (lt.getTurnoutType()>=LayoutTurnout.DOUBLE_XOVER) && 
						(lt.getLayoutBlock()!=null) ) {
					// have a crossover turnout with at least one block, check for multiple blocks
					if ( (lt.getLayoutBlockB()!=null) || (lt.getLayoutBlockC()!=null) ||
									(lt.getLayoutBlockD()!=null) ) {
						// have multiple blocks and therefore internal block boundaries
						if ((lt.getLayoutBlockB()!=null) && (lt.getLayoutBlock()!=lt.getLayoutBlockB())) {
							// have a AB block boundary, create a LayoutConnectivity
							c = new LayoutConnectivity(lt.getLayoutBlock(),lt.getLayoutBlockB());
							c.setXoverBoundary(lt,LayoutConnectivity.XOVER_BOUNDARY_AB);
							c.setDirection(computeDirection(lt.getCoordsA(),lt.getCoordsB()));
							checkConnectivity(c,found);
						}
						if ((lt.getTurnoutType()!=LayoutTurnout.LH_XOVER) && (lt.getLayoutBlockC()!=null) && 
													(lt.getLayoutBlock()!=lt.getLayoutBlockC())) {
							// have a AC block boundary, create a LayoutConnectivity
							c = new LayoutConnectivity(lt.getLayoutBlock(),lt.getLayoutBlockC());
							c.setXoverBoundary(lt,LayoutConnectivity.XOVER_BOUNDARY_AC);
							c.setDirection(computeDirection(lt.getCoordsA(),lt.getCoordsC()));
							checkConnectivity(c,found);
						}
						if ((lt.getLayoutBlockC()!=null) && (lt.getLayoutBlockD()!=null) && 
													(lt.getLayoutBlockC()!=lt.getLayoutBlockD())) {
							// have a CD block boundary, create a LayoutConnectivity
							c = new LayoutConnectivity(lt.getLayoutBlockC(),lt.getLayoutBlockD());
							c.setXoverBoundary(lt,LayoutConnectivity.XOVER_BOUNDARY_CD);
							c.setDirection(computeDirection(lt.getCoordsC(),lt.getCoordsD()));
							checkConnectivity(c,found);
						}
						if ((lt.getTurnoutType()!=LayoutTurnout.RH_XOVER) && (lt.getLayoutBlockB()!=null) && 
								(lt.getLayoutBlockD()!=null) && (lt.getLayoutBlockB()!=lt.getLayoutBlockD())) {
							// have a BD block boundary, create a LayoutConnectivity
							c = new LayoutConnectivity(lt.getLayoutBlockB(),lt.getLayoutBlockD());
							c.setXoverBoundary(lt,LayoutConnectivity.XOVER_BOUNDARY_BD);
							c.setDirection(computeDirection(lt.getCoordsB(),lt.getCoordsD()));
							checkConnectivity(c,found);
						}
					}
				}
			}		
		}
		// delete any LayoutConnectivity objects no longer needed
		for (int i = sz-1;i>=0;i--) {
			if (!found[i]) {
// djd debugging
//				LayoutConnectivity xx = (LayoutConnectivity)cList.get(i);
//				log.error("  Deleting Layout Connectivity - "+xx.getBlock1().getID()+", "+
//													xx.getBlock2().getID());
// end debugging
				cList.remove(i);
			}
		}
		blockConnectivityChanged = false;
	}
	// 
	private void checkConnectivity(LayoutConnectivity c,boolean[] found) {
		// initialize input LayoutConnectivity components
		LayoutBlock blk1 = c.getBlock1();
		LayoutBlock blk2 = c.getBlock2();
		int dir = c.getDirection();
		int rDir = c.getReverseDirection();
		TrackSegment track = c.getTrackSegment();
		Object connected = c.getConnectedObject();
		int type = c.getConnectedType();
		LayoutTurnout xOver = c.getXover();
		int xOverType = c.getXoverBoundaryType();
		// loop over connectivity list, looking for this layout connectivity
		boolean looking = true;
		for (int i = 0;(i<cList.size()) && looking;i++) {
			LayoutConnectivity lc = (LayoutConnectivity)cList.get(i);
			// compare input LayoutConnectivity with LayoutConnectivity from the list
			if (xOver==null) {
				// not a crossover block boundary
				if ( (blk1==lc.getBlock1()) && (blk2==lc.getBlock2()) && (track==lc.getTrackSegment()) &&
						(connected==lc.getConnectedObject()) && (type==lc.getConnectedType()) &&
							(dir==lc.getDirection()) ) {
					looking = false;
					found[i] = true;
				}
			}
			else {
				// boundary is in a crossover turnout
				if ( (xOver==lc.getXover()) && (xOverType==lc.getXoverBoundaryType()) ) {
					if ((blk1==lc.getBlock1()) && (blk2==lc.getBlock2()) && (dir==lc.getDirection()) ) {
						looking = false;
						found[i] = true;
					}
					else if ((blk2==lc.getBlock1()) && (blk1==lc.getBlock2()) && (rDir==lc.getDirection()) ) {
						looking = false;
						found[i] = true;
					}
				}
			}
		}
		// if not found in list, add it
		if (looking) {
			cList.add(c);
// djd debugging
//			log.error("  Adding LayoutConnectivity - "+blk1.getID()+", "+blk2.getID());
		}
	}
	
	// compute direction of vector from p1 to p2
	private int computeDirection(Point2D p1, Point2D p2) {
		double dh = p2.getX() - p1.getX();
		double dv = p2.getY() - p1.getY();
		int dir = Path.NORTH;
		double tanA;
		if (dv!=0.0) tanA = Math.abs(dh)/Math.abs(dv);
		else tanA = 10.0;
		if (tanA<0.38268) {
			// track is mostly vertical
			if (dv<0.0) dir = Path.NORTH;
			else dir = Path.SOUTH;
		}
		else if (tanA>2.4142) {
			// track is mostly horizontal
			if (dh>0.0) dir = Path.EAST;
			else dir = Path.WEST;
		}
		else {
			// track is between horizontal and vertical
			if ( (dv>0.0) && (dh>0.0) ) dir = Path.SOUTH + Path.EAST;
			else if ( (dv>0.0) && (dh<0.0) ) dir = Path.SOUTH + Path.WEST;
			else if ( (dv<0.0) && (dh<0.0) ) dir = Path.NORTH + Path.WEST;
			else dir = Path.NORTH + Path.EAST;
		}
		return dir;
	}
	
	/** 
	 * Searches for and adds BeanSetting's to a Path as needed.
	 * <P>
	 * This method starts at the entry point to the LayoutBlock given in the Path at 
	 *		the block boundary specified in the LayoutConnectivity. It follows the track
	 *		looking for turnout settings that are required for a train entering on this 
	 *		block boundary point to exit the block. If a required turnout setting is found, 
	 *		the turnout and its required state are used to create a BeanSetting, which is 
	 *		added to the Path. Such a setting can occur, for example, if a track enters
	 *		a right-handed turnout from either the diverging track or the continuing track.
	 * <P>
	 * If the track branches into two tracks (for example, by entering a right-handed turnout
	 *		via the throat track), the search is stopped. The search is also stopped when
	 *		the track reaches a different block (or an undefined block), or reaches an end 
	 *		bumper.
	 */
	public void addBeanSettings(Path p, LayoutConnectivity lc, LayoutBlock layoutBlock) {
		p.clearSettings();
		Object curConnection = null;
		Object prevConnection = null;
		int typeCurConnection = 0;
		BeanSetting bs = null;
		LayoutTurnout lt = null;
		Turnout t = null;
		// process object at block boundary
		if (lc.getBlock1()==layoutBlock) {
			// block1 is this LayoutBlock
			if (lc.getTrackSegment()!=null) {
				// connected object in this block is a track segment
				curConnection = lc.getTrackSegment();
				prevConnection = lc.getConnectedObject();
				typeCurConnection = LayoutEditor.TRACK;
			}
			else {
				// block boundary is internal to a crossover turnout
				lt = lc.getXover();
				prevConnection = (Object)lt;
				if ( (lt!=null) && (lt.getTurnout()!=null) ) {
					int type = lc.getXoverBoundaryType();
					if (type==LayoutConnectivity.XOVER_BOUNDARY_AB) {
						bs = new BeanSetting(lt.getTurnout(),Turnout.CLOSED);
						curConnection = lt.getConnectA();
					}
					else if (type==LayoutConnectivity.XOVER_BOUNDARY_CD) {
						bs = new BeanSetting(lt.getTurnout(),Turnout.CLOSED);
						curConnection = lt.getConnectC();
					}
					else if (type==LayoutConnectivity.XOVER_BOUNDARY_AC) {
						bs = new BeanSetting(lt.getTurnout(),Turnout.THROWN);
						curConnection = lt.getConnectA();
					}
					else if (type==LayoutConnectivity.XOVER_BOUNDARY_BD) {
						bs = new BeanSetting(lt.getTurnout(),Turnout.THROWN);
						curConnection = lt.getConnectB();
					}
					typeCurConnection = LayoutEditor.TRACK;
					p.addSetting(bs);
				}
			}
		}
		else {
			// block2 is this LayoutBlock
			if (lc.getConnectedObject()!=null) {
				// connected object in this block is a RH, LH, or WYE turnout or levelxing
				curConnection = lc.getConnectedObject();
				prevConnection = (Object)lc.getTrackSegment();
				typeCurConnection = lc.getConnectedType();
				if (typeCurConnection==LayoutEditor.TURNOUT_A) {
					// turnout throat, no bean setting needed and cannot follow Path any further
					curConnection=null;
				}
				else if (typeCurConnection==LayoutEditor.TURNOUT_B) {
					// continuing track of turnout
					if (((LayoutTurnout)curConnection).getContinuingSense()==Turnout.CLOSED) 
						bs = new BeanSetting(((LayoutTurnout)curConnection).getTurnout(),Turnout.CLOSED);
					else
						bs = new BeanSetting(((LayoutTurnout)curConnection).getTurnout(),Turnout.THROWN);
					p.addSetting(bs);
					prevConnection = curConnection;
					curConnection = ((LayoutTurnout)curConnection).getConnectA();
					typeCurConnection = LayoutEditor.TRACK;
				}
				else if (typeCurConnection==LayoutEditor.TURNOUT_C) {
					// diverging track of turnout
					if (((LayoutTurnout)curConnection).getContinuingSense()==Turnout.CLOSED) 
						bs = new BeanSetting(((LayoutTurnout)curConnection).getTurnout(),Turnout.THROWN);
					else
						bs = new BeanSetting(((LayoutTurnout)curConnection).getTurnout(),Turnout.CLOSED);
					p.addSetting(bs);
					prevConnection = curConnection;
					curConnection = ((LayoutTurnout)curConnection).getConnectA();
					typeCurConnection = LayoutEditor.TRACK;
				}
				// if level crossing, skip to the connected track segment on opposite side
				else if (typeCurConnection==LayoutEditor.LEVEL_XING_A) {
					prevConnection = curConnection;
					curConnection = ((LevelXing)curConnection).getConnectC();
					typeCurConnection = LayoutEditor.TRACK;
				}
				else if (typeCurConnection==LayoutEditor.LEVEL_XING_C) {
					prevConnection = curConnection;
					curConnection = ((LevelXing)curConnection).getConnectA();
					typeCurConnection = LayoutEditor.TRACK;
				}
				else if (typeCurConnection==LayoutEditor.LEVEL_XING_B) {
					prevConnection = curConnection;
					curConnection = ((LevelXing)curConnection).getConnectD();
					typeCurConnection = LayoutEditor.TRACK;
				}
				else if (typeCurConnection==LayoutEditor.LEVEL_XING_D) {
					prevConnection = curConnection;
					curConnection = ((LevelXing)curConnection).getConnectB();
					typeCurConnection = LayoutEditor.TRACK;
				}
			}
			else {
				// block boundary is internal to a crossover turnout
				lt = lc.getXover();
				prevConnection = (Object)lt;
				if ( (lt!=null) && (lt.getTurnout()!=null) ) {
					int type = lc.getXoverBoundaryType();
					if (type==LayoutConnectivity.XOVER_BOUNDARY_AB) {
						bs = new BeanSetting(lt.getTurnout(),Turnout.CLOSED);
						curConnection = lt.getConnectB();
					}
					else if (type==LayoutConnectivity.XOVER_BOUNDARY_CD) {
						bs = new BeanSetting(lt.getTurnout(),Turnout.CLOSED);
						curConnection = lt.getConnectD();
					}
					else if (type==LayoutConnectivity.XOVER_BOUNDARY_AC) {
						bs = new BeanSetting(lt.getTurnout(),Turnout.THROWN);
						curConnection = lt.getConnectC();
					}
					else if (type==LayoutConnectivity.XOVER_BOUNDARY_BD) {
						bs = new BeanSetting(lt.getTurnout(),Turnout.THROWN);
						curConnection = lt.getConnectD();
					}
					typeCurConnection = LayoutEditor.TRACK;
					p.addSetting(bs);
				}
			}
		}
		// follow path through this block - done when reaching another block, or a branching of Path
		while (curConnection!=null) {
			if (typeCurConnection == LayoutEditor.TRACK) {
				// track segment is current connection
				if (((TrackSegment)curConnection).getLayoutBlock()!=layoutBlock) {
					curConnection = null;
				}
				else {
					// skip over to other end of Track Segment
					if (((TrackSegment)curConnection).getConnect1()==prevConnection) {
						prevConnection = curConnection;
						typeCurConnection = ((TrackSegment)curConnection).getType2();
						curConnection = ((TrackSegment)curConnection).getConnect2();
					}
					else {
						prevConnection = curConnection;
						typeCurConnection = ((TrackSegment)curConnection).getType1();
						curConnection = ((TrackSegment)curConnection).getConnect1();
					}
					// skip further if positionable point (possible anchor point)
					if (typeCurConnection ==  LayoutEditor.POS_POINT) {
						PositionablePoint pt = (PositionablePoint)curConnection;
						if (pt.getType() == PositionablePoint.END_BUMPER) {
							// reached end of track
							curConnection = null;
						}
						else {
							// at an anchor point, find track segment on other side
							TrackSegment track = null;
							if ((Object)pt.getConnect1() == prevConnection) track = pt.getConnect2();
							else track = pt.getConnect1();
							// check for block boundary
							if ( (track==null) || (track.getLayoutBlock()!=layoutBlock) ) {
								// moved outside of block - anchor point was a block boundary -OR-
								//		reached the end of the defined track
								curConnection = null;
							}
							else {
								prevConnection = curConnection;
								curConnection = (Object)track;
								typeCurConnection = LayoutEditor.TRACK;
							}
						}
					}									
				}
			}
			else if ( (typeCurConnection >= LayoutEditor.TURNOUT_A) && 
											(typeCurConnection <= LayoutEditor.TURNOUT_D) ) {
				lt = (LayoutTurnout)curConnection;
				// test for crossover turnout
				if (lt.getTurnoutType() <= LayoutTurnout.WYE_TURNOUT) {
					// have RH, LH, or WYE turnout
					if (lt.getLayoutBlock()!=layoutBlock) {
						// moving to another block
						curConnection = null;
					}
					else {
						// turnout is in current block, test connection point
						if (typeCurConnection == LayoutEditor.TURNOUT_A) {
							// turnout throat, no bean setting needed and cannot follow possible path any further
							curConnection=null;
						}
						else if (typeCurConnection==LayoutEditor.TURNOUT_B) {
							// continuing track of turnout, add a bean setting
							if (lt.getContinuingSense()==Turnout.CLOSED) 
								bs = new BeanSetting(lt.getTurnout(),Turnout.CLOSED);
							else
								bs = new BeanSetting(lt.getTurnout(),Turnout.THROWN);
							p.addSetting(bs);
							prevConnection = curConnection;
							curConnection = lt.getConnectA();
							typeCurConnection = LayoutEditor.TRACK;
						}
						else if (typeCurConnection==LayoutEditor.TURNOUT_C) {
							// diverging track of turnout
							if (lt.getContinuingSense()==Turnout.CLOSED) 
								bs = new BeanSetting(lt.getTurnout(),Turnout.THROWN);
							else
								bs = new BeanSetting(lt.getTurnout(),Turnout.CLOSED);
							p.addSetting(bs);
							prevConnection = curConnection;
							curConnection = lt.getConnectA();
							typeCurConnection = LayoutEditor.TRACK;
						}
					}
				}
				else if (lt.getTurnoutType() == LayoutTurnout.DOUBLE_XOVER) {
					// have a double crossover turnout, cannot follow possible path any further
					curConnection = null;
				}
				else if (lt.getTurnoutType() == LayoutTurnout.RH_XOVER) {
					// have a right-handed crossover turnout
					if ( (typeCurConnection==LayoutEditor.TURNOUT_A) || 
							(typeCurConnection==LayoutEditor.TURNOUT_C) ) {
						// entry is at turnout throat, cannot follow possible path any further
						curConnection = null;
					}
					else if (typeCurConnection==LayoutEditor.TURNOUT_B) {
						// entry is at continuing track of turnout 
						if (lt.getLayoutBlock()!=layoutBlock) {
							// left current block before reaching turnout
							curConnection = null;
						}
						else {
							bs = new BeanSetting(lt.getTurnout(),Turnout.CLOSED);
							p.addSetting(bs);
							prevConnection = curConnection;
							curConnection = lt.getConnectA();
							typeCurConnection = LayoutEditor.TRACK;
						}
					}						
					else if (typeCurConnection==LayoutEditor.TURNOUT_D) {
						// entry is at continuing track of turnout 
						if (lt.getLayoutBlockC()!=layoutBlock) {
							// left current block before reaching turnout
							curConnection = null;
						}
						else {
							bs = new BeanSetting(lt.getTurnout(),Turnout.CLOSED);
							p.addSetting(bs);
							prevConnection = curConnection;
							curConnection = lt.getConnectC();
							typeCurConnection = LayoutEditor.TRACK;
						}
					}						
				}
				else if (lt.getTurnoutType() == LayoutTurnout.LH_XOVER) {
					// have a left-handed crossover turnout
					if ( (typeCurConnection==LayoutEditor.TURNOUT_B) || 
							(typeCurConnection==LayoutEditor.TURNOUT_D) ) {
						// entry is at turnout throat, cannot follow possible path any further
						curConnection = null;
					}
					else if (typeCurConnection==LayoutEditor.TURNOUT_A) {
						// entry is at continuing track of turnout 
						if (lt.getLayoutBlockB()!=layoutBlock) {
							// left current block before reaching turnout
							curConnection = null;
						}
						else {
							bs = new BeanSetting(lt.getTurnout(),Turnout.CLOSED);
							p.addSetting(bs);
							prevConnection = curConnection;
							curConnection = lt.getConnectB();
							typeCurConnection = LayoutEditor.TRACK;
						}
					}						
					else if (typeCurConnection==LayoutEditor.TURNOUT_C) {
						// entry is at continuing track of turnout 
						if (lt.getLayoutBlockD()!=layoutBlock) {
							// left current block
							curConnection = null;
						}
						else {
							bs = new BeanSetting(lt.getTurnout(),Turnout.CLOSED);
							p.addSetting(bs);
							prevConnection = curConnection;
							curConnection = lt.getConnectD();
							typeCurConnection = LayoutEditor.TRACK;
						}
					}						
				}
			}
			else if (typeCurConnection==LayoutEditor.LEVEL_XING_A) {
				// have a level crossing connected at A
				if (((LevelXing)curConnection).getLayoutBlockAC()!=layoutBlock) {
					// moved outside of this block
					curConnection = null;
				}
				else {
					// move to other end of this section of this level crossing track
					prevConnection = curConnection;
					curConnection = ((LevelXing)curConnection).getConnectC();
					typeCurConnection = LayoutEditor.TRACK;
				}
			}
			else if (typeCurConnection==LayoutEditor.LEVEL_XING_B) {
				// have a level crossing connected at B
				if (((LevelXing)curConnection).getLayoutBlockBD()!=layoutBlock) {
					// moved outside of this block
					curConnection = null;
				}
				else {
					// move to other end of this section of this level crossing track
					prevConnection = curConnection;
					curConnection = ((LevelXing)curConnection).getConnectD();
					typeCurConnection = LayoutEditor.TRACK;
				}
			}
			else if (typeCurConnection==LayoutEditor.LEVEL_XING_C) {
				// have a level crossing connected at C
				if (((LevelXing)curConnection).getLayoutBlockAC()!=layoutBlock) {
					// moved outside of this block
					curConnection = null;
				}
				else {
					// move to other end of this section of this level crossing track
					prevConnection = curConnection;
					curConnection = ((LevelXing)curConnection).getConnectA();
					typeCurConnection = LayoutEditor.TRACK;
				}
			}
			else if (typeCurConnection==LayoutEditor.LEVEL_XING_D) {
				// have a level crossing connected at D
				if (((LevelXing)curConnection).getLayoutBlockBD()!=layoutBlock) {
					// moved outside of this block
					curConnection = null;
				}
				else {
					// move to other end of this section of this level crossing track
					prevConnection = curConnection;
					curConnection = ((LevelXing)curConnection).getConnectB();
					typeCurConnection = LayoutEditor.TRACK;
				}
			}
		}
	}

	
    /**
     * Clean up when this object is no longer needed.  Should not
     * be called while the object is still displayed; see remove()
     */
    public void dispose() {
    }

	// initialize logging
    static org.apache.log4j.Category log = org.apache.log4j.Category.getInstance(LayoutEditorAuxTools.class.getName());
}
