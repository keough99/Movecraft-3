/*
 * This file is part of Movecraft.
 *
 *     Movecraft is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Movecraft is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Movecraft.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.countercraft.movecraft.utils;

import com.earth2me.essentials.User;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.items.StorageChestItem;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.utils.datastructures.InventoryTransferHolder;
import net.countercraft.movecraft.utils.datastructures.SignTransferHolder;
import net.countercraft.movecraft.utils.FastBlockChanger.ChunkUpdater;
import net.countercraft.movecraft.utils.datastructures.CommandBlockTransferHolder;
import net.countercraft.movecraft.utils.datastructures.StorageCrateTransferHolder;
import net.countercraft.movecraft.utils.datastructures.TransferData;
import net.minecraft.server.v1_9_R1.BlockPosition;
import net.minecraft.server.v1_9_R1.ChunkCoordIntPair;
import net.minecraft.server.v1_9_R1.EnumSkyBlock;
import net.minecraft.server.v1_9_R1.IBlockData;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.CommandBlock;
import org.bukkit.craftbukkit.v1_9_R1.CraftChunk;
import org.bukkit.craftbukkit.v1_9_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_9_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_9_R1.util.CraftMagicNumbers;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.blocks.SignBlock;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DefaultFlag;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.logging.Level;

import net.minecraft.server.v1_9_R1.EntityTNTPrimed;

import org.bukkit.event.entity.ExplosionPrimeEvent;

public class MapUpdateManager extends BukkitRunnable {
	private final HashMap<World, ArrayList<MapUpdateCommand>> updates = new HashMap<World, ArrayList<MapUpdateCommand>>();
	private final HashMap<World, ArrayList<EntityUpdateCommand>> entityUpdates = new HashMap<World, ArrayList<EntityUpdateCommand>>();
    private final HashMap<World, ArrayList<ItemDropUpdateCommand>> itemDropUpdates = new HashMap<World, ArrayList<ItemDropUpdateCommand>>();
		
	private MapUpdateManager() {
	}

	public static MapUpdateManager getInstance() {
		return MapUpdateManagerHolder.INSTANCE;
	}

	private static class MapUpdateManagerHolder {
		private static final MapUpdateManager INSTANCE = new MapUpdateManager();
	}
	
	private void updateBlock(MapUpdateCommand m, World w, Map<MovecraftLocation, TransferData> dataMap, Set<net.minecraft.server.v1_9_R1.Chunk> chunks, Set<Chunk> cmChunks, HashMap<MovecraftLocation, Byte> origLightMap, boolean placeDispensers) {
		MovecraftLocation workingL = m.getNewBlockLocation();
		final int[] blocksToBlankOut = new int[]{ 54, 61, 62, 63, 68, 116, 117, 145, 146, 149, 150, 154, 158 };		

		int x = workingL.getX();
		int y = workingL.getY();
		int z = workingL.getZ();
		Chunk chunk=null;

		int newTypeID = m.getTypeID();

		if((newTypeID==152 || newTypeID==26) && !placeDispensers) {
			return;
		}
			
		
		chunk = w.getBlockAt( x, y, z ).getChunk();

		net.minecraft.server.v1_9_R1.Chunk c = null;
		Chunk cmC = null;
		if(Settings.CompatibilityMode) {
			cmC = w.getBlockAt( x, y, z ).getChunk();
		} else {
			c = ( ( CraftChunk ) chunk ).getHandle();
		}

		byte data=m.getDataID();
		
		if(newTypeID==23 && !placeDispensers) {
			newTypeID=44;
			data=8;
		}
		
		boolean success = false;

		if(Settings.CompatibilityMode) { 

			int origType=w.getBlockAt( x, y, z ).getTypeId();
			byte origData=w.getBlockAt( x, y, z ).getData();

			if(origType!=newTypeID || origData!=data) {
				boolean doBlankOut=(Arrays.binarySearch(blocksToBlankOut,newTypeID)>=0);
				if(doBlankOut) {
					w.getBlockAt(x, y, z).setType(org.bukkit.Material.AIR);
				}
				
				if(origType==149 || origType==150) { // necessary because bukkit does not handle comparators correctly. This code does not prevent console spam, but it does prevent chunk corruption
					w.getBlockAt(x, y, z).setType(org.bukkit.Material.SIGN_POST);
					BlockState state=w.getBlockAt( x, y, z ).getState();
					if(state instanceof Sign) { // for some bizarre reason the block is sometimes not a sign, which crashes unless I do this
						Sign s=(Sign)state;
						s.setLine(0, "PLACEHOLDER");
//						s.update();   FROGGG
					}
					w.getBlockAt(x, y, z).setType(org.bukkit.Material.AIR);
					}
				if((newTypeID==149 || newTypeID==150) && m.getWorldEditBaseBlock()==null) {
					w.getBlockAt( x, y, z ).setTypeIdAndData( newTypeID, data, false );
				} else {
					if(m.getWorldEditBaseBlock()==null) {
						w.getBlockAt( x, y, z ).setTypeIdAndData( newTypeID, data, false );
					} else {
						w.getBlockAt( x, y, z ).setTypeIdAndData( ((BaseBlock)m.getWorldEditBaseBlock()).getType(), (byte)((BaseBlock)m.getWorldEditBaseBlock()).getData(), false );
						BaseBlock bb=(BaseBlock)m.getWorldEditBaseBlock();
						if(m.getWorldEditBaseBlock() instanceof SignBlock) {
							BlockState state=w.getBlockAt( x, y, z ).getState();
							Sign s=(Sign)state;
							for(int i=0; i<((SignBlock)m.getWorldEditBaseBlock()).getText().length; i++) {
								s.setLine( i, ((SignBlock)m.getWorldEditBaseBlock()).getText()[i] );
							}
							s.update();
						}
					}
				}
			}
			if ( !cmChunks.contains( cmC ) ) {
				cmChunks.add( cmC );
			}
		} else {
			int origType=w.getBlockAt( x, y, z ).getTypeId();
			byte origData=w.getBlockAt( x, y, z ).getData();
/*
			if(origType!=newTypeID || origData!=data) {
				ChunkUpdater fChunk=FastBlockChanger.getInstance().getChunk(w, x>>4, z>>4, false);
				IBlockData ibd=CraftMagicNumbers.getBlock(newTypeID).fromLegacyData(data);//net.minecraft.server.v1_9_R1.Block.getByCombinedId(newTypeID+(data<<12));
				boolean doBlankOut=(Arrays.binarySearch(blocksToBlankOut,newTypeID)>=0);
				if(doBlankOut) {
					fChunk.setBlock(new BlockPosition(x, y, z), CraftMagicNumbers.getBlock(org.bukkit.Material.AIR).fromLegacyData(0));
				}
				if(origType==149 || origType==150) { // necessary because bukkit does not handle comparators correctly. This code does not prevent console spam, but it does prevent chunk corruption
					w.getBlockAt(x, y, z).setType(org.bukkit.Material.SIGN_POST);
					BlockState state=w.getBlockAt( x, y, z ).getState();
					if(state instanceof Sign) { // for some bizarre reason the block is sometimes not a sign, which crashes unless I do this
						Sign s=(Sign)state;
						s.setLine(0, "PLACEHOLDER");
//						s.update();   FROGGG
					}
					w.getBlockAt(x, y, z).setType(org.bukkit.Material.AIR);
					}
				fChunk.setBlock(new BlockPosition(x, y, z), ibd);
			}*/
//			 removing to try new fast block changer system
			BlockPosition position = new BlockPosition(x, y, z);
			
			if((origType==149 || origType==150) && m.getWorldEditBaseBlock()==null) { // bukkit can't remove comparators safely, it screws up the NBT data. So turn it to a sign, then remove it.

				c.a( position, CraftMagicNumbers.getBlock(org.bukkit.Material.AIR).fromLegacyData(0));
				c.a( position, CraftMagicNumbers.getBlock(org.bukkit.Material.SIGN_POST).fromLegacyData(0));
				
				BlockState state=w.getBlockAt( x, y, z ).getState();
				Sign s=(Sign)state;
				s.setLine(0, "PLACEHOLDER");
				s.update();
				c.a(position, CraftMagicNumbers.getBlock(org.bukkit.Material.SIGN_POST).fromLegacyData(0));
				success = c.a( position, CraftMagicNumbers.getBlock(newTypeID).fromLegacyData(data) ) != null;
				if ( !success ) {
					w.getBlockAt( x, y, z ).setTypeIdAndData( newTypeID, data, false );
				}
				if ( !chunks.contains( c ) ) {
					chunks.add( c );
				}
			} else {
		
				if(origType!=newTypeID || origData!=data) {
					boolean doBlankOut=(Arrays.binarySearch(blocksToBlankOut,newTypeID)>=0);
					if(doBlankOut) {
						c.a( position, CraftMagicNumbers.getBlock(0).fromLegacyData(0) );
						w.getBlockAt(x, y, z).setType(org.bukkit.Material.AIR);
					}
					

					if(m.getWorldEditBaseBlock()==null) {
						success = c.a( position, CraftMagicNumbers.getBlock(newTypeID).fromLegacyData(data) ) != null;
					} else {
						success = c.a( position, CraftMagicNumbers.getBlock(newTypeID).fromLegacyData(data) ) != null;
						if(m.getWorldEditBaseBlock() instanceof SignBlock) {
							BlockState state=w.getBlockAt( x, y, z ).getState();
							Sign s=(Sign)state;
							for(int i=0; i<((SignBlock)m.getWorldEditBaseBlock()).getText().length; i++) {
								s.setLine( i, ((SignBlock)m.getWorldEditBaseBlock()).getText()[i] );
							}
							s.update();
						}						
					}

				} else {
					success=true;
				}
				if ( !success ) {
					if(m.getWorldEditBaseBlock()==null) {
						w.getBlockAt( x, y, z ).setTypeIdAndData( newTypeID, data, false );
					} else {
						w.getBlockAt( x, y, z ).setTypeIdAndData( ((BaseBlock)m.getWorldEditBaseBlock()).getType(), (byte)((BaseBlock)m.getWorldEditBaseBlock()).getData(), false );
						if(m.getWorldEditBaseBlock() instanceof SignBlock) {
							BlockState state=w.getBlockAt( x, y, z ).getState();
							Sign s=(Sign)state;
							for(int i=0; i<((SignBlock)m.getWorldEditBaseBlock()).getText().length; i++) {
								s.setLine( i, ((SignBlock)m.getWorldEditBaseBlock()).getText()[i] );
							}
							s.update();
						}
					}
				}

				if ( !chunks.contains( c ) ) {
					chunks.add( c );
				}
			}//*/
			
		}						

	}

	private void updateData(Map<MovecraftLocation, TransferData> dataMap, World w) {
		// Restore block specific information
		for ( MovecraftLocation l : dataMap.keySet() ) { // everything but signs first
			try {
				TransferData transferData = dataMap.get( l );
				if ( transferData instanceof SignTransferHolder ) {
					//do nothing... TODO: make this cleaner
				} else if ( transferData instanceof StorageCrateTransferHolder ) {
					Inventory inventory = Bukkit.createInventory( null, 27, String.format( I18nSupport.getInternationalisedString( "Item - Storage Crate name" ) ) );
					inventory.setContents( ( ( StorageCrateTransferHolder ) transferData ).getInvetory() );
					StorageChestItem.setInventoryOfCrateAtLocation( inventory, l, w );
	
				} else if ( transferData instanceof InventoryTransferHolder ) {
					InventoryTransferHolder invData = ( InventoryTransferHolder ) transferData;
					InventoryHolder inventoryHolder = ( InventoryHolder ) w.getBlockAt( l.getX(), l.getY(), l.getZ() ).getState();
					inventoryHolder.getInventory().setContents( invData.getInvetory() );
				} else if ( transferData instanceof CommandBlockTransferHolder) {
					CommandBlockTransferHolder cbData=(CommandBlockTransferHolder) transferData;
					CommandBlock cblock=(CommandBlock) w.getBlockAt( l.getX(), l.getY(), l.getZ() ).getState();
					cblock.setCommand(cbData.getText());
					cblock.setName(cbData.getName());
					cblock.update();
				}
				w.getBlockAt( l.getX(), l.getY(), l.getZ() ).setData( transferData.getData() );
			} catch ( IndexOutOfBoundsException e ) {
				Movecraft.getInstance().getLogger().log( Level.SEVERE, "Severe error in map updater" );
			} catch (IllegalArgumentException e) {
	                                Movecraft.getInstance().getLogger().log( Level.SEVERE, "Severe error in map updater" );
			}
		}
		for ( MovecraftLocation l : dataMap.keySet() ) { // now do signs
			try {
				TransferData transferData = dataMap.get( l );
	
				if ( transferData instanceof SignTransferHolder ) {
	
					SignTransferHolder signData = ( SignTransferHolder ) transferData;
					BlockState bs=w.getBlockAt( l.getX(), l.getY(), l.getZ() ).getState();
					if(bs instanceof Sign) {
						Sign sign = ( Sign ) bs;
						for ( int i = 0; i < signData.getLines().length; i++ ) {
							sign.setLine( i, signData.getLines()[i] );
						}
						if(Settings.AllowCrewSigns && signData.getLines()[0].equalsIgnoreCase("Crew:")) {
							String crewName=signData.getLines()[1];
							Player crewPlayer=Movecraft.getInstance().getServer().getPlayer(crewName);
							if(crewPlayer!=null) {
								Location loc=sign.getLocation();
								loc=loc.subtract(0, 1, 0);
								if(w.getBlockAt(loc).getType().equals(Material.BED_BLOCK)) {
									crewPlayer.setBedSpawnLocation(loc);
									if(Settings.SetHomeToCrewSign==true)

										if (Movecraft.getInstance().getEssentialsPlugin() != null){
                                            User u = Movecraft.getInstance().getEssentialsPlugin().getUser(crewPlayer);
                                            u.setHome("home", loc);
                                        }
                                        
								}
							}
						}
						if(sign.getLines()[0].equalsIgnoreCase("Contacts:")) {
							MovecraftLocation mloc=new MovecraftLocation(sign.getLocation().getBlockX(),sign.getLocation().getBlockY(),sign.getLocation().getBlockZ());
							if(CraftManager.getInstance().getCraftsInWorld(w)!=null) {
								Craft foundCraft=null;
								for(Craft c : CraftManager.getInstance().getCraftsInWorld(w)) {
									if(MathUtils.playerIsWithinBoundingPolygon(c.getHitBox(),c.getMinX(),c.getMinZ(),mloc))
										foundCraft=c;
								}
								if(foundCraft!=null) {
									boolean foundContact=false;
									int signLine=1;
									for(Craft tcraft : CraftManager.getInstance().getCraftsInWorld(w)) {
										long cposx=foundCraft.getMaxX()+foundCraft.getMinX();
										long cposy=foundCraft.getMaxY()+foundCraft.getMinY();
										long cposz=foundCraft.getMaxZ()+foundCraft.getMinZ();
										cposx=cposx>>1;
										cposy=cposy>>1;
										cposz=cposz>>1;
										long tposx=tcraft.getMaxX()+tcraft.getMinX();
										long tposy=tcraft.getMaxY()+tcraft.getMinY();
										long tposz=tcraft.getMaxZ()+tcraft.getMinZ();
										tposx=tposx>>1;
										tposy=tposy>>1;
										tposz=tposz>>1;
										long diffx=cposx-tposx;
										long diffy=cposy-tposy;
										long diffz=cposz-tposz;
										long distsquared=Math.abs(diffx)*Math.abs(diffx);
										distsquared+=Math.abs(diffy)*Math.abs(diffy);
										distsquared+=Math.abs(diffz)*Math.abs(diffz);
										long detectionRange=0;
										if(tposy>tcraft.getW().getSeaLevel()) {
											detectionRange=(long) (Math.sqrt(tcraft.getOrigBlockCount())*tcraft.getType().getDetectionMultiplier());
										} else {
											detectionRange=(long) (Math.sqrt(tcraft.getOrigBlockCount())*tcraft.getType().getUnderwaterDetectionMultiplier());
										}
										if(distsquared<detectionRange*detectionRange && tcraft.getNotificationPlayer()!=foundCraft.getNotificationPlayer()) {
											// craft has been detected				
											foundContact=true;
											String notification=""+ChatColor.BLUE;
											notification+=tcraft.getType().getCraftName();
											if(notification.length()>9)
												notification=notification.substring(0, 7);
											notification+=" ";
											notification+=(int)Math.sqrt(distsquared);
											if(Math.abs(diffx) > Math.abs(diffz))
												if(diffx<0)
													notification+=" E";
												else
													notification+=" W";
											else
												if(diffz<0)
													notification+=" S";
												else
													notification+=" N";
											if(signLine<=3) {
												sign.setLine(signLine, notification);
												signLine++;
											}
										}
									}
									if(signLine<4) {
										for(int i=signLine; i<4; i++) {
											sign.setLine(i, "");
										}
									}
								}
							} else {
								sign.setLine(1, "");
								sign.setLine(2, "");
								sign.setLine(3, "");
							}
							
						} else if(sign.getLines()[0].equalsIgnoreCase("Status:")) {
							MovecraftLocation mloc=new MovecraftLocation(sign.getLocation().getBlockX(),sign.getLocation().getBlockY(),sign.getLocation().getBlockZ());
							Craft foundCraft=null;
							for(Craft c : CraftManager.getInstance().getCraftsInWorld(w)) {
								if(MathUtils.playerIsWithinBoundingPolygon(c.getHitBox(),c.getMinX(),c.getMinZ(),mloc))
									foundCraft=c;
							}
							if(foundCraft!=null) {
								int fuel=0;
								int totalBlocks=0;
								HashMap<Integer, Integer> foundBlocks = new HashMap<Integer, Integer>();
								for (MovecraftLocation ml : foundCraft.getBlockList()) {
									Integer blockID = w.getBlockAt(ml.getX(), ml.getY(), ml.getZ()).getTypeId();

									if (foundBlocks.containsKey(blockID)) {
										Integer count = foundBlocks.get(blockID);
										if (count == null) { 
											foundBlocks.put(blockID, 1);
										} else {
											foundBlocks.put(blockID, count + 1);
										}
									} else {
										foundBlocks.put(blockID, 1);
									}
									
									if (blockID == 61) {
										Block b = w.getBlockAt(ml.getX(), ml.getY(), ml.getZ());
										InventoryHolder inventoryHolder = (InventoryHolder) w.getBlockAt(ml.getX(), ml.getY(),
												ml.getZ()).getState();
										if (inventoryHolder.getInventory().contains(263)
												|| inventoryHolder.getInventory().contains(173)) {
											ItemStack[] istack=inventoryHolder.getInventory().getContents();
											for(ItemStack i : istack) {
												if(i!=null) {
													if(i.getTypeId()==263) {
														fuel+=i.getAmount()*8;
													}
													if(i.getTypeId()==173) {
														fuel+=i.getAmount()*80;
													}
												}
											}
										}
									}
									if (blockID != 0) {
										totalBlocks++;
									}
								}
								int signLine=1;
								int signColumn=0;
								for(ArrayList<Integer> alFlyBlockID : foundCraft.getType().getFlyBlocks().keySet()) {
									int flyBlockID=alFlyBlockID.get(0);
									Double minimum=foundCraft.getType().getFlyBlocks().get(alFlyBlockID).get(0);
									if(foundBlocks.containsKey(flyBlockID) && minimum>0) { // if it has a minimum, it should be considered for sinking consideration
										int amount=foundBlocks.get(flyBlockID);
										Double percentPresent=(double) (amount*100/totalBlocks);
										int deshiftedID=flyBlockID;
										if(deshiftedID>10000) {
											deshiftedID=(deshiftedID-10000)>>4;
										}
										String signText="";
										if(percentPresent>minimum*1.3) {
											signText+=ChatColor.GREEN;
										} else if(percentPresent>minimum*1.1) {
											signText+=ChatColor.YELLOW;											
										} else {
											signText+=ChatColor.RED;											
										}
										if(deshiftedID==152) {
											signText+="R";
										} else if(deshiftedID==42) {
											signText+="I";
										} else {
											signText+=CraftMagicNumbers.getBlock(deshiftedID).getName().substring(0, 1);											
										}
										
										signText+=" ";
										signText+=percentPresent.intValue();
										signText+="/";
										signText+=minimum.intValue();
										signText+="  ";
										if(signColumn==0) {
											sign.setLine(signLine, signText);
											signColumn++;
										} else if(signLine<3) {
											String existingLine=sign.getLine(signLine);
											existingLine+=signText;
											sign.setLine(signLine, existingLine);
											signLine++;
											signColumn=0;
										}
									}
								}
								String fuelText="";
								Integer fuelRange=(int) ((fuel*(1+foundCraft.getType().getCruiseSkipBlocks()))/foundCraft.getType().getFuelBurnRate());
								if(fuelRange>1000) {
									fuelText+=ChatColor.GREEN;
								} else if(fuelRange>100) {
									fuelText+=ChatColor.YELLOW;
								} else {
									fuelText+=ChatColor.RED;									
								}
								fuelText+="Fuel range:";
								fuelText+=fuelRange.toString();
								sign.setLine(3, fuelText);
							}

						}

						for(Player p : w.getPlayers()) { // this is necessary because signs do not get updated client side correctly without refreshing the chunks, which causes a memory leak in the clients							
							int playerChunkX=p.getLocation().getBlockX()>>4;
							int playerChunkZ=p.getLocation().getBlockZ()>>4;
							if(Math.abs(playerChunkX-sign.getChunk().getX())<Bukkit.getServer().getViewDistance())
								if(Math.abs(playerChunkZ-sign.getChunk().getZ())<Bukkit.getServer().getViewDistance()) {
									p.sendBlockChange(sign.getLocation(), 63, (byte) 0);
									p.sendBlockChange(sign.getLocation(), sign.getTypeId(), sign.getRawData());
									
								}
						}
						sign.update( true, false );
					}
				}
				w.getBlockAt( l.getX(), l.getY(), l.getZ() ).setData( transferData.getData() );
			} catch ( IndexOutOfBoundsException e ) {
				Movecraft.getInstance().getLogger().log( Level.SEVERE, "Severe error in map updater" );
			} catch (IllegalArgumentException e) {
	                                Movecraft.getInstance().getLogger().log( Level.SEVERE, "Severe error in map updater" );
			}
		}
	}

	/*
	private void runQueue(final ArrayList<MapUpdateCommand> queuedMapUpdateCommands, final ArrayList<Boolean> queuedPlaceDispensers, final World w, final Set<net.minecraft.server.v1_9_R1.Chunk> chunks, final Set<Chunk> cmChunks, 
			  			  final HashMap<MovecraftLocation, Byte> origLightMap, final Map<MovecraftLocation, TransferData> dataMap, final List<MapUpdateCommand> updatesInWorld, final Map<MovecraftLocation, List<EntityUpdateCommand>> entityMap) {
		int numToRun=queuedMapUpdateCommands.size();
		if(numToRun>Settings.BlockQueueChunkSize)
			numToRun=Settings.BlockQueueChunkSize;
		long start=System.currentTimeMillis();
		for(int i=0;i<numToRun;i++) {
			MapUpdateCommand m=queuedMapUpdateCommands.get(0);
			updateBlock(m, w, dataMap, chunks, cmChunks, origLightMap, queuedPlaceDispensers.get(0));			
			queuedMapUpdateCommands.remove(0);
			queuedPlaceDispensers.remove(0);
		}
		long end=System.currentTimeMillis();
		if(queuedMapUpdateCommands.size()>0) {	
			BukkitTask nextQueueRun = new BukkitRunnable() {
				@Override
				public void run() {
					try {
					runQueue(queuedMapUpdateCommands,queuedPlaceDispensers, w, chunks, cmChunks, origLightMap, dataMap, updatesInWorld, entityMap);
					} catch (Exception e) {
						StringWriter sw = new StringWriter();
						PrintWriter pw = new PrintWriter(sw);
						e.printStackTrace(pw);
						sw.toString(); 
						Movecraft.getInstance().getLogger().log( Level.SEVERE, sw.toString() );
					}
				}
			}.runTaskLater( Movecraft.getInstance(), ( (end-start)/50 ) );
		} else {
			// all done, do final cleanup with sign data, inventories, etc
			updateData(dataMap, w);
			
			if(CraftManager.getInstance().getCraftsInWorld(w)!=null) {
				
				// and set all crafts that were updated to not processing
				for ( MapUpdateCommand c : updatesInWorld ) {
					if(c!=null) {
						Craft craft=c.getCraft();
						if(craft!=null) {
							if(!craft.isNotProcessing()) {
								craft.setProcessing(false);
								if(Settings.Debug) {
									long finish=System.currentTimeMillis();
									Movecraft.getInstance().getServer().broadcastMessage("Time from last cruise to update (ms): "+(finish-craft.getLastCruiseUpdate()));
								}
							}
						}

					}						
				}
			}
			if(Settings.CompatibilityMode==false) {
				// send updates to client
				
				// Commented out because this is now handled by the fast block updater
//				for ( MapUpdateCommand c : updatesInWorld ) {
//					Location loc=new Location(w,c.getNewBlockLocation().getX(),c.getNewBlockLocation().getY(),c.getNewBlockLocation().getZ());
//					w.getBlockAt(loc).getState().update();
//				}

				
				
				//				for ( net.minecraft.server.v1_8_R3.Chunk c : chunks ) {
//					c.initLighting();
//				}

			}
			
			

		}
	}
	*/
	
	public void run() {
		if ( updates.isEmpty() ) return;

		long startTime=System.currentTimeMillis();

		final int[] fragileBlocks = new int[]{ 26, 34, 50, 55, 63, 64, 65, 68, 69, 70, 71, 72, 75, 76, 77, 93, 94, 96, 131, 132, 143, 147, 148, 149, 150, 151, 171, 323, 324, 330, 331, 356, 404 };
		Arrays.sort(fragileBlocks);
				
		for ( World w : updates.keySet() ) {
			if ( w != null ) {
				List<MapUpdateCommand> updatesInWorld = updates.get( w );
				List<EntityUpdateCommand> entityUpdatesInWorld = entityUpdates.get( w );
                                List<ItemDropUpdateCommand> itemDropUpdatesInWorld = itemDropUpdates.get( w );
				Map<MovecraftLocation, List<EntityUpdateCommand>> entityMap = new HashMap<MovecraftLocation, List<EntityUpdateCommand>>();
                                Map<MovecraftLocation, List<ItemDropUpdateCommand>> itemMap = new HashMap<MovecraftLocation, List<ItemDropUpdateCommand>>();
				Map<MovecraftLocation, TransferData> dataMap = new HashMap<MovecraftLocation, TransferData>();
				HashMap<MovecraftLocation, Byte> origLightMap = new HashMap<MovecraftLocation, Byte>();
				Set<net.minecraft.server.v1_9_R1.Chunk> chunks = null; 
				Set<Chunk> cmChunks = null;
//				ArrayList<MapUpdateCommand> queuedMapUpdateCommands = new ArrayList<MapUpdateCommand>();
//				ArrayList<Boolean> queuedPlaceDispensers = new ArrayList<Boolean>();

				if(Settings.CompatibilityMode) {
					cmChunks = new HashSet<Chunk>();					
				} else {
					chunks = new HashSet<net.minecraft.server.v1_9_R1.Chunk>();
				}
				
				// Make sure all chunks are loaded
				for ( MapUpdateCommand c : updatesInWorld ) {
					if(c!=null) {
						if(c.getNewBlockLocation()!=null) {
							if(!w.isChunkLoaded(c.getNewBlockLocation().getX()>>4, c.getNewBlockLocation().getZ()>>4)) {
								w.loadChunk(c.getNewBlockLocation().getX()>>4, c.getNewBlockLocation().getZ()>>4);
							}
						}
						if(c.getOldBlockLocation()!=null) {
							if(!w.isChunkLoaded(c.getOldBlockLocation().getX()>>4, c.getOldBlockLocation().getZ()>>4)) {
								w.loadChunk(c.getOldBlockLocation().getX()>>4, c.getOldBlockLocation().getZ()>>4);
							}
						}
					}
				}
                                
				// Preprocessing
				for ( MapUpdateCommand c : updatesInWorld ) {
					MovecraftLocation l;
					if(c!=null)
						l = c.getOldBlockLocation();
					else 
						l = null;
					
					if ( l != null ) {
						// keep track of the light levels that were present before moving the craft
//						origLightMap.put(l, w.getBlockAt(l.getX(), l.getY(), l.getZ()).getLightLevel());
						
						// keep track of block data for later reconstruction
						TransferData blockDataPacket = getBlockDataPacket( w.getBlockAt( l.getX(), l.getY(), l.getZ() ).getState(), c.getRotation() );
						if ( blockDataPacket != null ) {
							dataMap.put( c.getNewBlockLocation(), blockDataPacket );
						}
						
						//remove dispensers and replace them with half slabs to prevent them firing during reconstruction
						if(w.getBlockAt( l.getX(), l.getY(), l.getZ() ).getTypeId()==23) {
							MapUpdateCommand blankCommand=new MapUpdateCommand(c.getOldBlockLocation(), 23, c.getDataID(), c.getCraft());
//							if(Settings.CompatibilityMode) {
//								queuedMapUpdateCommands.add(blankCommand);
//								queuedPlaceDispensers.add(false);
//							} else 
								updateBlock(blankCommand, w, dataMap, chunks, cmChunks, origLightMap, false);
						}
						//remove redstone blocks and replace them with stone to prevent redstone activation during reconstruction
						if(w.getBlockAt( l.getX(), l.getY(), l.getZ() ).getTypeId()==152) {
							MapUpdateCommand blankCommand=new MapUpdateCommand(c.getOldBlockLocation(), 1, (byte)0, c.getCraft());
//							if(Settings.CompatibilityMode) {
//								queuedMapUpdateCommands.add(blankCommand);
//								queuedPlaceDispensers.add(false);
//							} else 
								updateBlock(blankCommand, w, dataMap, chunks, cmChunks, origLightMap, false);
						}
						//remove water and lava blocks and replace them with stone to prevent spillage during reconstruction
						if(w.getBlockAt( l.getX(), l.getY(), l.getZ() ).getTypeId()>=8 && w.getBlockAt( l.getX(), l.getY(), l.getZ() ).getTypeId()<=11) {
							MapUpdateCommand blankCommand=new MapUpdateCommand(c.getOldBlockLocation(), 0, (byte)0, c.getCraft());
							updateBlock(blankCommand, w, dataMap, chunks, cmChunks, origLightMap, false);
						}
					}					
				}
                                
				// move entities
				if(entityUpdatesInWorld!=null) {
					for( EntityUpdateCommand i : entityUpdatesInWorld) {
						if(i!=null) {
							MovecraftLocation entityLoc=new MovecraftLocation(i.getNewLocation().getBlockX(), i.getNewLocation().getBlockY()-1, i.getNewLocation().getBlockZ());
							if(!entityMap.containsKey(entityLoc)) {
								List<EntityUpdateCommand> entUpdateList=new ArrayList<EntityUpdateCommand>();
								entUpdateList.add(i);
								entityMap.put(entityLoc, entUpdateList);
							} else {
								List<EntityUpdateCommand> entUpdateList=entityMap.get(entityLoc);
								entUpdateList.add(i);
							}
							if(i.getEntity() instanceof Player) {
								// send the blocks around the player first
								Player p=(Player)i.getEntity();
								for ( MapUpdateCommand muc : updatesInWorld ) {
									if(muc!=null) {
										int disty=Math.abs(muc.getNewBlockLocation().getY()-i.getNewLocation().getBlockY());
										int distx=Math.abs(muc.getNewBlockLocation().getX()-i.getNewLocation().getBlockX());
										int distz=Math.abs(muc.getNewBlockLocation().getZ()-i.getNewLocation().getBlockZ());
										if(disty<2 && distx<2 && distz<2) {
											updateBlock(muc, w, dataMap, chunks, cmChunks, origLightMap, false);
											Location nloc=new Location(w, muc.getNewBlockLocation().getX(), muc.getNewBlockLocation().getY(), muc.getNewBlockLocation().getZ());
											p.sendBlockChange(nloc, muc.getTypeID(), muc.getDataID());
										}
									}
								}
							}
							i.getEntity().teleport(i.getNewLocation());
						}
					}
				}
				
				// Place any blocks that replace "fragiles", other than other fragiles
				for ( MapUpdateCommand i : updatesInWorld ) {
					if(i!=null) {
						if(i.getTypeID()>=0) {
							int prevType=w.getBlockAt(i.getNewBlockLocation().getX(), i.getNewBlockLocation().getY(), i.getNewBlockLocation().getZ()).getTypeId();
							boolean prevIsFragile=(Arrays.binarySearch(fragileBlocks,prevType)>=0);
							boolean isFragile=(Arrays.binarySearch(fragileBlocks,i.getTypeID())>=0);
							if(prevIsFragile && (!isFragile)) {
//								if(Settings.CompatibilityMode) {
//									queuedMapUpdateCommands.add(i);
//									queuedPlaceDispensers.add(false);
//								} else 
									updateBlock(i, w, dataMap, chunks, cmChunks, origLightMap, false);
							}
							if(prevIsFragile && isFragile) {
								MapUpdateCommand blankCommand=new MapUpdateCommand(i.getNewBlockLocation(), 0, (byte)0, i.getCraft());
//								if(Settings.CompatibilityMode) {
//									queuedMapUpdateCommands.add(blankCommand);
//									queuedPlaceDispensers.add(false);
//								} else 
									updateBlock(blankCommand, w, dataMap, chunks, cmChunks, origLightMap, false);
							}
						}
					}
				}
				
				// Perform core block updates, don't do "fragiles" yet. Don't do Dispensers or air yet either
				for ( MapUpdateCommand m : updatesInWorld ) {
					if(m!=null) {
						boolean isFragile=(Arrays.binarySearch(fragileBlocks,m.getTypeID())>=0);
						
						if(!isFragile) {
							// a TypeID less than 0 indicates an explosion
							if(m.getTypeID()<0) {
								if(m.getTypeID()<-10) { // don't bother with tiny explosions
									float explosionPower=m.getTypeID();
									explosionPower=0.0F-explosionPower/100.0F;
                                                                        Location loc = new Location(w, m.getNewBlockLocation().getX()+0.5, m.getNewBlockLocation().getY()+0.5, m.getNewBlockLocation().getZ());
                                                                        this.createExplosion(loc, explosionPower);
									//w.createExplosion(m.getNewBlockLocation().getX()+0.5, m.getNewBlockLocation().getY()+0.5, m.getNewBlockLocation().getZ()+0.5, explosionPower);
								}
							} else {
	//							if(Settings.CompatibilityMode) {
//									queuedMapUpdateCommands.add(m);
//									queuedPlaceDispensers.add(false);
	//							} else 
									updateBlock(m, w, dataMap, chunks, cmChunks, origLightMap, false);
							}
						}
						
						// if the block you just updated had any entities on it, move them. If they are moving, add in their motion to the craft motion
						if( entityMap.containsKey(m.getNewBlockLocation()) && !Settings.CompatibilityMode) {
							List<EntityUpdateCommand> mapUpdateList=entityMap.get(m.getNewBlockLocation());
							for(EntityUpdateCommand entityUpdate : mapUpdateList) {
								Entity entity=entityUpdate.getEntity();

								entity.teleport(entityUpdate.getNewLocation());
							}
							entityMap.remove(m.getNewBlockLocation());
						}
					}
	
				}

				// Fix redstone and other "fragiles"				
				for ( MapUpdateCommand i : updatesInWorld ) {
					if(i!=null) {
						boolean isFragile=(Arrays.binarySearch(fragileBlocks,i.getTypeID())>=0);
						if(isFragile) {
		//					if(Settings.CompatibilityMode) {
//								queuedMapUpdateCommands.add(i);
//								queuedPlaceDispensers.add(false);
		//					} else 
								updateBlock(i, w, dataMap, chunks, cmChunks, origLightMap, false);
						}
					}
				}

				for ( MapUpdateCommand i : updatesInWorld ) {
					if(i!=null) {
						// Put Dispensers back in now that the ship is reconstructed
						if(i.getTypeID()==23 || i.getTypeID()==152) {
		//					if(Settings.CompatibilityMode) {
//								queuedMapUpdateCommands.add(i);
//								queuedPlaceDispensers.add(true);
		//					} else 
								updateBlock(i, w, dataMap, chunks, cmChunks, origLightMap, true);					
						}
						
					}
				}
				
				/*for ( MapUpdateCommand i : updatesInWorld ) {
					if(i!=null) {
						// Place air
						if(i.getTypeID()==0) {
							if(Settings.CompatibilityMode) {
								queuedMapUpdateCommands.add(i);
								queuedPlaceDispensers.add(true);
							} else 
								updateBlock(i, w, dataMap, chunks, cmChunks, origLightMap, true);					
						}
						
					}
				}*/

				for ( MapUpdateCommand i : updatesInWorld ) {
					if(i!=null) {
						// Place beds
						if(i.getTypeID()==26) {
		//					if(Settings.CompatibilityMode) {
//								queuedMapUpdateCommands.add(i);
//								queuedPlaceDispensers.add(true);
		//					} else 
								updateBlock(i, w, dataMap, chunks, cmChunks, origLightMap, true);					
						}
						
					}
				}

				for ( MapUpdateCommand i : updatesInWorld ) {
					if(i!=null) {
						// Place fragiles again, in case they got screwed up the first time
						boolean isFragile=(Arrays.binarySearch(fragileBlocks,i.getTypeID())>=0);
						if(isFragile) {
		//					if(Settings.CompatibilityMode) {
//								queuedMapUpdateCommands.add(i);
//								queuedPlaceDispensers.add(true);
		//					} else 
								updateBlock(i, w, dataMap, chunks, cmChunks, origLightMap, true);
						}						
					}
				}
				
/*				// move entities again
				if(!Settings.CompatibilityMode)
					for(MovecraftLocation i : entityMap.keySet()) {
						List<EntityUpdateCommand> mapUpdateList=entityMap.get(i);
							for(EntityUpdateCommand entityUpdate : mapUpdateList) {
								Entity entity=entityUpdate.getEntity();
								entity.teleport(entityUpdate.getNewLocation());
							}
					}*/

				// put in smoke or effects
				for ( MapUpdateCommand i : updatesInWorld ) {
					if(i!=null) {
						if(i.getSmoke()==1) {
							Location loc=new Location(w, i.getNewBlockLocation().getX(), i.getNewBlockLocation().getY(),  i.getNewBlockLocation().getZ());
							w.playEffect(loc, Effect.SMOKE, 4);
						}
					}
				}
			
					updateData(dataMap, w);
					
					if(CraftManager.getInstance().getCraftsInWorld(w)!=null) {
						
						// and set all crafts that were updated to not processing
						for ( MapUpdateCommand c : updatesInWorld ) {
							if(c!=null) {
								Craft craft=c.getCraft();
								if(craft!=null) {
									if(!craft.isNotProcessing()) {
										craft.setProcessing(false);
									}
								}

							}						
						}
					}
					// send updates to clients
					for ( MapUpdateCommand c : updatesInWorld ) {
						if(c!=null) {
							Location loc=new Location(w,c.getNewBlockLocation().getX(),c.getNewBlockLocation().getY(),c.getNewBlockLocation().getZ());
							w.getBlockAt(loc).getState().update();
						}
					}
					// queue chunks for lighting recalc
					if(Settings.CompatibilityMode==false) {
						for(net.minecraft.server.v1_9_R1.Chunk c : chunks) {
							ChunkUpdater fChunk=FastBlockChanger.getInstance().getChunk(c.world,c.locX,c.locZ,true);
							for(int bx=0;bx<16;bx++) {
								for(int bz=0;bz<16;bz++) {
									for(int by=0;by<4;by++) {
										fChunk.bits[bx][bz][by]=Long.MAX_VALUE;										
									}
								}
							}

							fChunk.last_modified=System.currentTimeMillis();
						}
					}
					
					long endTime=System.currentTimeMillis();
					if(Settings.Debug) {
						Movecraft.getInstance().getServer().broadcastMessage("Map update took (ms): "+(endTime-startTime));
					}


                                //drop harvested yield 
                                if(itemDropUpdatesInWorld!=null) {
					for( ItemDropUpdateCommand i : itemDropUpdatesInWorld) {
						if(i!=null) {                                                        
                                                        final World world = w;
                                                        final Location loc = i.getLocation();
                                                        final ItemStack stack = i.getItemStack();
							if(i.getItemStack() instanceof ItemStack) {
								// drop Item
								BukkitTask dropTask = new BukkitRunnable() {
									@Override
									public void run() {
                                                                            world.dropItemNaturally(loc, stack);
									}
								}.runTaskLater( Movecraft.getInstance(), ( 20 * 1 ) );
							}
						}
					}
				}
			}
		}
		
		updates.clear();
		entityUpdates.clear();
                itemDropUpdates.clear();
	}
        
        public boolean addWorldUpdate( World w, MapUpdateCommand[] mapUpdates, EntityUpdateCommand[] eUpdates, ItemDropUpdateCommand[] iUpdates) {
		
        if(mapUpdates!=null)	 {
	        ArrayList<MapUpdateCommand> get = updates.get( w );	
			if ( get != null ) {
				updates.remove( w ); 
				ArrayList<MapUpdateCommand> tempUpdates = new ArrayList<MapUpdateCommand>();
		        tempUpdates.addAll(Arrays.asList(mapUpdates));
				get.addAll( tempUpdates );
			} else {
				get = new ArrayList<MapUpdateCommand>(Arrays.asList(mapUpdates));		
			}
			updates.put(w, get);
        }

		//now do entity updates
		if(eUpdates!=null) {
			ArrayList<EntityUpdateCommand> eGet = entityUpdates.get( w );
			if ( eGet != null ) {
				entityUpdates.remove( w ); 
				ArrayList<EntityUpdateCommand> tempEUpdates = new ArrayList<EntityUpdateCommand>();
	            tempEUpdates.addAll(Arrays.asList(eUpdates));
				eGet.addAll( tempEUpdates );
			} else {
				eGet = new ArrayList<EntityUpdateCommand>(Arrays.asList(eUpdates));
			}
			entityUpdates.put(w, eGet);
		}
                
                //now do item drop updates
		if(iUpdates!=null) {
			ArrayList<ItemDropUpdateCommand> iGet = itemDropUpdates.get( w );
			if ( iGet != null ) {
				entityUpdates.remove( w );
				ArrayList<ItemDropUpdateCommand> tempIDUpdates = new ArrayList<ItemDropUpdateCommand>();
	            tempIDUpdates.addAll(Arrays.asList(iUpdates));
				iGet.addAll( tempIDUpdates );
			} else {
				iGet = new ArrayList<ItemDropUpdateCommand>(Arrays.asList(iUpdates));
			}
			itemDropUpdates.put(w, iGet);
		}
                
		return false;
	}
	
	private boolean setContainsConflict( ArrayList<MapUpdateCommand> set, MapUpdateCommand c ) {
		for ( MapUpdateCommand command : set ) {
			if( command!=null && c!=null)
				if ( command.getNewBlockLocation().equals( c.getNewBlockLocation() ) ) {
					return true;
				}
		}

		return false;
	}

	private boolean arrayContains( int[] oA, int o ) {
		for ( int testO : oA ) {
			if ( testO == o ) {
				return true;
			}
		}

		return false;
	}

	private TransferData getBlockDataPacket( BlockState s, Rotation r ) {
		if ( BlockUtils.blockHasNoData( s.getTypeId() ) ) {
			return null;
		}

		byte data = s.getRawData();

		if ( BlockUtils.blockRequiresRotation( s.getTypeId() ) && r != Rotation.NONE ) {
			data = BlockUtils.rotate( data, s.getTypeId(), r );
		}

		switch ( s.getTypeId() ) {
			case 23:
			case 54:
			case 61:
			case 62:
			case 117:
			case 146:
			case 158:
			case 154:
				// Data and Inventory
				if(( ( InventoryHolder ) s ).getInventory().getSize()==54) {
					Movecraft.getInstance().getLogger().log( Level.SEVERE, "ERROR: Double chest detected. This is not supported." );
					throw new IllegalArgumentException("INVALID BLOCK");
				}
				ItemStack[] contents = ( ( InventoryHolder ) s ).getInventory().getContents().clone();
				( ( InventoryHolder ) s ).getInventory().clear();
				return new InventoryTransferHolder( data, contents );

			case 68:
			case 63:
				// Data and sign lines
				Sign signData=(Sign)s;
				return new SignTransferHolder( data, signData.getLines() );

			case 33:
				MovecraftLocation l = MathUtils.bukkit2MovecraftLoc( s.getLocation() );
				Inventory i = StorageChestItem.getInventoryOfCrateAtLocation( l, s.getWorld() );
				if ( i != null ) {
					StorageChestItem.removeInventoryAtLocation( s.getWorld(), l );
					return new StorageCrateTransferHolder( data, i.getContents() );
				} else {
					return new TransferData( data );
				}
				
			case 137:
				CommandBlock cblock=(CommandBlock)s;
				return new CommandBlockTransferHolder( data, cblock.getCommand(), cblock.getName());

			default:
				return null;

		}
	}
        
	  
    
    private void createExplosion(Location loc, float explosionPower){
//        if (Settings.CompatibilityMode){
            //using other-explosion flag ... isn't secure
        	boolean explosionblocked=false;
    		if(Movecraft.getInstance().getWorldGuardPlugin()!=null) {
    			ApplicableRegionSet set = Movecraft.getInstance().getWorldGuardPlugin().getRegionManager(loc.getWorld()).getApplicableRegions(loc);
    			if(set.allows(DefaultFlag.OTHER_EXPLOSION)==false) {
    				explosionblocked=true;
    			}
    		}
    		if(!explosionblocked)
    			loc.getWorld().createExplosion(loc.getX()+0.5,loc.getY()+0.5, loc.getZ()+0.5, explosionPower);
            return;
//        }

//        loc.getWorld().createExplosion(loc.getX()+0.5,loc.getY()+0.5, loc.getZ()+0.5, explosionPower);
        //correct explosion ... tnt event ... may be changed to any else entity type
/*        EntityTNTPrimed e = new EntityTNTPrimed(((CraftWorld)loc.getWorld()).getHandle()); // this is the code that causes pre 1.8.3 builds of Spigot to fail
        e.setLocation(loc.getX(),loc.getBlockY(), loc.getBlockZ(), 0f, 0f);
        e.setSize(0.89F, 0.89F);
        e.setInvisible(true);
        org.bukkit.craftbukkit.v1_8_R3.CraftWorld craftWorld = (CraftWorld) loc.getWorld();
        org.bukkit.craftbukkit.v1_8_R3.CraftServer server = craftWorld.getHandle().getServer();
        
        ExplosionPrimeEvent event = new ExplosionPrimeEvent((org.bukkit.entity.Explosive) org.bukkit.craftbukkit.v1_8_R3.entity.CraftEntity.getEntity(server, e));
        event.setRadius(explosionPower);
        server.getPluginManager().callEvent(event);

        if (!event.isCancelled()) {
            craftWorld.getHandle().createExplosion(e, loc.getX() + 0.5D , loc.getY() + 0.5D , loc.getZ() + 0.5D , event.getRadius(), event.getFire(), true);
        }
  */      
    }

}
