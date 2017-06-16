/*
 * Created on Feb 8, 2007
 */
package org.seqcode.projects.seqview.components;

import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;

import org.seqcode.genome.Genome;
import org.seqcode.genome.location.Gene;
import org.seqcode.genome.location.Region;
import org.seqcode.gsebricks.GeneFactoryLoader;
import org.seqcode.gsebricks.verbs.*;
import org.seqcode.gsebricks.verbs.assignment.*;

import java.io.File;
import java.io.PrintWriter;

public class RegionAnnotationPanel extends JPanel implements RegionList {

	private static GeneFactoryLoader geneFactoryLoader = new GeneFactoryLoader();
	private Genome genome;
	private RegionToRegionAnnotations annotations;
	private OverlappingRegionExpander<Region> expander;
	private Expander<Region, Gene> lastgenerator;

	private JTextField upField, downField;

	private DefaultComboBoxModel geneSourceModel;
	private JComboBox geneSourceCombo;

	private DefaultListModel eventModel, geneModel;
	private JList eventList, geneList;

	private JButton annotateButton, saveLocationsButton, saveGenesButton, sortButton, saveGenesFastaButton,
			saveEventsFastaButton;

	private RegionPanel regionPanel;
	private Collection<Region> eventsToAdd, allEvents;
	private boolean changedevents;
	private JMenuBar jmb;
	private JFrame frame;

	private TaskRunnable taskRunnable;
	private LinkedList<Runnable> tasks;

	private class TaskRunnable implements Runnable {

		private boolean continueRunning;

		public TaskRunnable() {
			continueRunning = true;
		}

		public void stop() {
			continueRunning = false;
		}

		public void run() {
			while (continueRunning) {
				Runnable nextRunnable = null;
				synchronized (this) {
					nextRunnable = tasks.isEmpty() ? null : tasks.removeFirst();
					if (nextRunnable == null) {
						try {
							wait();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}

				if (nextRunnable != null) {
					nextRunnable.run();
				}
			}
		}

		private synchronized void waitOnMe() {
		}
	}

	private void addTaskedRunnable(Runnable r) {
		synchronized (taskRunnable) {
			tasks.addLast(r);
			taskRunnable.notifyAll();
		}
	}

	public RegionAnnotationPanel(RegionPanel rp, Collection<Region> events) {
		super();

		tasks = new LinkedList<Runnable>();
		taskRunnable = new TaskRunnable();
		Thread t = new Thread(taskRunnable);
		t.start();

		regionPanel = rp;
		genome = rp.getGenome();
		expander = null;
		allEvents = new ArrayList<Region>();
		if (events != null) {
			allEvents.addAll(events);
		}

		eventsToAdd = new ArrayList<Region>();
		changedevents = false;
		setLayout(new BorderLayout());

		JPanel upPanel = new JPanel();
		upPanel.setLayout(new BorderLayout());
		upPanel.add(upField = new JTextField("8000"), BorderLayout.NORTH);
		upPanel.setBorder(new TitledBorder("Upstream:"));

		JPanel downPanel = new JPanel();
		downPanel.setLayout(new BorderLayout());
		downPanel.add(downField = new JTextField("2000"), BorderLayout.NORTH);
		downPanel.setBorder(new TitledBorder("Downstream:"));

		geneSourceModel = new DefaultComboBoxModel();
		geneSourceCombo = new JComboBox(geneSourceModel);
		JPanel sourcePanel = new JPanel();
		sourcePanel.setLayout(new BorderLayout());
		sourcePanel.add(geneSourceCombo, BorderLayout.NORTH);
		sourcePanel.setBorder(new TitledBorder("Gene Source:"));

		JPanel paramPanel = new JPanel();
		paramPanel.setLayout(new GridLayout(1, 3));
		paramPanel.add(upPanel);
		paramPanel.add(downPanel);
		paramPanel.add(sourcePanel);
		add(paramPanel, BorderLayout.NORTH);

		eventModel = new DefaultListModel();
		geneModel = new DefaultListModel();
		eventList = new JList(eventModel);
		geneList = new JList(geneModel);

		JPanel genePanel = new JPanel();
		genePanel.setLayout(new BorderLayout());
		genePanel.add(new JScrollPane(geneList), BorderLayout.CENTER);
		genePanel.setBorder(new TitledBorder("Genes:"));

		JPanel eventPanel = new JPanel();
		eventPanel.setLayout(new BorderLayout());
		eventPanel.add(new JScrollPane(eventList), BorderLayout.CENTER);
		eventPanel.setBorder(new TitledBorder("Binding Events:"));

		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		splitPane.add(eventPanel, JSplitPane.LEFT);
		splitPane.add(genePanel, JSplitPane.RIGHT);

		add(splitPane, BorderLayout.CENTER);
		final RegionAnnotationPanel caller = this;

		jmb = new JMenuBar();
		JMenu fileMenu = new JMenu("File");
		JMenuItem item = new JMenuItem("Save Locations");
		fileMenu.add(item);
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				caller.saveLocations();
			}
		});

		item = new JMenuItem("Save Genes");
		fileMenu.add(item);
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				caller.saveGenes();
			}
		});

		item = new JMenuItem("Save Locations as FASTA");
		fileMenu.add(item);
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ArrayList<Region> regions = new ArrayList<Region>();
				for (int i = 0; i < eventModel.size(); i++) {
					regions.add((Region) eventModel.get(i));
				}
				new SaveRegionsAsFasta(regions);
			}
		});

		item = new JMenuItem("Save Genes as FASTA");
		fileMenu.add(item);
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ArrayList<Region> regions = new ArrayList<Region>();
				for (int i = 0; i < geneModel.size(); i++) {
					regions.add((Region) geneModel.get(i));
				}
				new SaveRegionsAsFasta(regions);
			}
		});

		item = new JMenuItem("Close");
		fileMenu.add(item);
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				frame.dispose();
			}
		});

		JMenu annotMenu = new JMenu("Annotate");
		item = new JMenuItem("Get Genes");
		annotMenu.add(item);
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Runnable r = new Runnable() {
					public void run() {
						annotate();
					}
				};

				addTaskedRunnable(r);
			}
		});

		JMenu sortMenu = new JMenu("Sorting");
		item = new JMenuItem("Sort Genes");
		sortMenu.add(item);
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				caller.sortGenes();
			}
		});

		jmb.add(fileMenu);
		jmb.add(annotMenu);
		jmb.add(sortMenu);

		TreeSet<String> sources = new TreeSet<String>(geneFactoryLoader.getTypes(genome));
		String first = null;
		for (String source : sources) {
			if (first == null) {
				first = source;
			}
			geneSourceModel.addElement(source);
		}
		if (first != null) {
			geneSourceModel.setSelectedItem(first);
		}

		for (Region event : allEvents) {
			eventModel.addElement(event);
		}

		eventList.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
					int row = eventList.locationToIndex(e.getPoint());
					Region region = (Region) eventModel.get(row);
					if (region != null && regionPanel != null) {
						Region existing = regionPanel.getRegion();
						int halfsize = Math.abs(existing.getEnd() - existing.getStart()) / 2;
						int center = Math.abs(region.getEnd() + region.getStart()) / 2;
						regionPanel.setRegion(new Region(region.getGenome(), region.getChrom(), center - halfsize,
								center + halfsize));
					}
				}
			}
		});

		geneList.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
					int row = geneList.locationToIndex(e.getPoint());
					Region region = (Region) geneModel.get(row);
					if (region != null && regionPanel != null) {
						regionPanel.setRegion(region);
					}
				}
			}
		});
	}

	public JMenuBar getMenuBar() {
		return jmb;
	}

	public void addRegion(Region r) {
		allEvents.add(r);
		changedevents = true;
		synchronized (eventsToAdd) {
			eventsToAdd.add(r);
		}
		SwingUtilities.invokeLater(new ListModelAdder(eventsToAdd, eventModel));
	}

	public int regionListSize() {
		return eventModel.size();
	}

	public Region regionAt(int i) {
		return (Region) eventModel.getElementAt(i);
	}

	public RegionToRegionAnnotations getAnnotations(JProgressBar pbar) {
		if (expander == null || changedevents) {
			expander = new OverlappingRegionExpander<Region>(allEvents);
			changedevents = false;
		}

		RegionToRegionAnnotations result = new RegionToRegionAnnotations(genome, (Expander<Region, Region>) expander,
				pbar);
		lastgenerator = result.getGeneGenerator();
		return result;
	}

	public void annotate() {
		System.out.println("Annotating: \"annotate()\" called.");

		clearGenes();

		JFrame progressFrame = new JFrame();
		JProgressBar bar = new JProgressBar(0, 1);
		bar.setValue(0);
		bar.setStringPainted(true);
		Container cnt = (Container) progressFrame.getContentPane();
		cnt.setLayout(new GridLayout(2, 1));
		cnt.add(new JLabel("Annotating Genes..."));
		cnt.add(bar);
		progressFrame.setVisible(true);
		progressFrame.pack();

		RegionToRegionAnnotations annotations = getAnnotations(bar);
		progressFrame.dispose();
		System.err.println("Annotating " + annotations.getNumItems());
		ArrayList targets = new ArrayList();
		for (int i = 0; i < annotations.getNumItems(); i++) {
			Region event = annotations.getItem(i);
			targets.addAll(annotations.getAnnotations(event, "genes"));
		}
		SwingUtilities.invokeLater(new ListModelAdder(targets, geneModel));

	}

	public void sortGenes() {
		Object[] genes = new Object[geneModel.size()];
		geneModel.copyInto(genes);
		Arrays.sort(genes, new GeneorRegionComparator());
		clearGenes();
		SwingUtilities.invokeLater(new ListModelAdder(genes, geneModel));
	}

	public void saveLocations() {
		JFileChooser chooser;
		chooser = new JFileChooser(new File(System.getProperty("user.dir")));
		int v = chooser.showSaveDialog(null);
		if (v == JFileChooser.APPROVE_OPTION) {
			try {
				File f = chooser.getSelectedFile();
				PrintWriter writer = new PrintWriter(f);
				for (int i = 0; i < eventModel.getSize(); i++) {
					writer.println(eventModel.get(i).toString());
				}
				writer.close();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	public void saveGenes() {
		JFileChooser chooser;
		chooser = new JFileChooser(new File(System.getProperty("user.dir")));
		int v = chooser.showSaveDialog(null);
		if (v == JFileChooser.APPROVE_OPTION) {
			try {
				File f = chooser.getSelectedFile();
				PrintWriter writer = new PrintWriter(f);
				for (int i = 0; i < geneModel.getSize(); i++) {
					writer.println(geneModel.get(i).toString());
				}
				writer.close();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	public void clearGenes() {
		geneModel.clear();
	}

	public static class Frame extends JFrame {
		private RegionAnnotationPanel annotationPanel;

		public Frame(RegionAnnotationPanel bea) {
			super("Region Annotations");
			bea.frame = this;
			annotationPanel = bea;
			setJMenuBar(bea.getMenuBar());
			Container c = (Container) getContentPane();
			c.setLayout(new BorderLayout());
			c.add(annotationPanel, BorderLayout.CENTER);

			setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			pack();
			setVisible(true);
		}
	}
}
