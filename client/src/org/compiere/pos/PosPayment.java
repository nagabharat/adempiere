/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 Adempiere, Inc. All Rights Reserved.               *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 *****************************************************************************/
package org.compiere.pos;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Properties;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;

import net.miginfocom.swing.MigLayout;

import org.adempiere.plaf.AdempierePLAF;
import org.compiere.apps.ADialog;
import org.compiere.apps.AppsAction;
import org.compiere.model.MCurrency;
import org.compiere.model.MInvoice;
import org.compiere.model.MLookup;
import org.compiere.model.MLookupFactory;
import org.compiere.model.MPOS;
import org.compiere.model.MPOSKey;
import org.compiere.model.MPayment;
import org.compiere.model.MPaymentValidate;
import org.compiere.swing.CButton;
import org.compiere.swing.CComboBox;
import org.compiere.swing.CDialog;
import org.compiere.swing.CLabel;
import org.compiere.swing.CPanel;
import org.compiere.swing.CTextField;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.ValueNamePair;

/**
 * 
 * Payment dialog
 * 
 *  @author Dixon Martinez, ERPCYA 
 *  @author Susanne Calderón Schöningh, Systemhaus Westfalia
 *  
 *  @version $Id: PosPayment.java,v 2.0 2015/09/01 00:00:00 scalderon
 */

public class PosPayment extends CDialog implements PosKeyListener, VetoableChangeListener, ActionListener {
	private static final long serialVersionUID = 1961106531807910948L;

	@Override
	public void actionPerformed(ActionEvent e) {
		
		if ( e.getSource().equals(fTenderAmt) || e.getSource().equals(fPayAmt) )
		{
			
			String tenderAmt = fTenderAmt.getText() != null ? fTenderAmt.getText() : "0";
			String payAmt = fPayAmt.getText() != null ? fPayAmt.getText() : "0";			
			
			BigDecimal tender = new BigDecimal( getNumber(Env.getCtx(), tenderAmt, DisplayType.String).toString() );
			BigDecimal pay = new BigDecimal( getNumber(Env.getCtx(), payAmt, DisplayType.String).toString());
			
			if ( tender.compareTo(Env.ZERO) != 0 )
			{
				fReturnAmt.setValue(tender.subtract(pay));
			}
			return;
		}
		
		if ( e.getSource().equals(fCreditNotes) )
		{
			BigDecimal openamt = new BigDecimal( fTotal.getText() );
			String ID = ((ValueNamePair) fCreditNotes.getSelectedItem()).getValue();
			MInvoice cn = new MInvoice(p_ctx, Integer.parseInt(ID), null);
			BigDecimal payamtmax = cn.getOpenAmt().negate();
			BigDecimal payamt = openamt.compareTo(payamtmax) >=0? payamtmax:openamt;
			fPayAmt.setValue(payamt);
			fTenderAmt.setValue(payamt);
			BigDecimal tender = new BigDecimal( fTenderAmt.getText() );
			if ( tender.compareTo(Env.ZERO) != 0 )
			{
				fReturnAmt.setValue(tender.subtract(payamt));
			}
			return;
		}

		if ( e.getSource().equals(f_bProcess))
		{
			BigDecimal tender = new BigDecimal( fTenderAmt.getText() );
			if ( tender.compareTo(Env.ZERO)== 0 )
			{
				ADialog.warn(0, p_posPanel, Msg.getMsg(p_ctx, "Tender amount zero"));
				return;
			}
			
			// Process Payment: first Process Order (if needed)
			if ( !p_order.isProcessed() && !p_order.processOrder() )
			{
				ADialog.warn(0, p_posPanel, Msg.getMsg(p_ctx, "PosOrderProcessFailed"));
				return;
			}

			// Then, process payment
			processPayment();
		}
		if ( e.getSource().equals(f_bCancel))
		{
			dispose();
			return;
		}

		setTotals();

		super.actionPerformed(e);
	} // actionPerformed

	/**
	 * Processes different kinds of payment types
	 * 
	 */
	private void processPayment() {

		try {

			String tenderType = ((ValueNamePair) tenderTypePick.getValue()).getID();
			BigDecimal amt = new BigDecimal(getNumber(Env.getCtx(), fPayAmt.getText(), DisplayType.String).toString());

			if ( tenderType.equals(MPayment.TENDERTYPE_Cash) )
			{
				p_posPanel.m_order.payCash(amt);
			}
			else if ( tenderType.equals(MPayment.TENDERTYPE_Check) )
			{
				p_posPanel.m_order.payCheck(amt,fCheckAccountNo.getText(), fCheckRouteNo.getText(), fCheckNo.getText());
				p_posPanel.f_order.openCashDrawer();
			}
			else if ( tenderType.equals(MPayment.TENDERTYPE_CreditCard) )
			{
				String error = null;
				error = MPaymentValidate.validateCreditCardExp(fCCardMonth.getText());
				if ( error != null && !error.isEmpty() )
				{
					ADialog.warn(0, p_posPanel, error);
					return;
				}
				int month = MPaymentValidate.getCreditCardExpMM(fCCardMonth.getText());
				int year = MPaymentValidate.getCreditCardExpYY(fCCardMonth.getText());

				String type = ((ValueNamePair) fCCardType.getSelectedItem()).getValue();
				error = MPaymentValidate.validateCreditCardNumber(fCCardNo.getText(), type);
				if ( error != null && !error.isEmpty() )
				{
					ADialog.warn(0, p_posPanel, error);
					return;
				}
				p_posPanel.m_order.payCreditCard(amt, fCCardName.getText(),
						month, year, fCCardNo.getText(), fCCardVC.getText(), type);
				p_posPanel.f_order.openCashDrawer();
			}
			else if ( tenderType.equals(MPayment.TENDERTYPE_Account) )
			{
				p_posPanel.m_order.payCash(amt);
				p_posPanel.f_order.openCashDrawer();
			}
			else if ( tenderType.equals("F") )
			{
				String ID = ((ValueNamePair) fCreditNotes.getSelectedItem()).getValue();
				MInvoice cn = new MInvoice(p_ctx, Integer.parseInt(ID), null);
				p_posPanel.m_order.payCreditNote(cn, amt);
			}

			else if ( tenderType.equals("N") )
			{
			}
			else
			{
				ADialog.warn(0, this, Msg.getMsg(Env.getCtx(), "Unsupported payment type"));
			}


			p_posPanel.f_order.openCashDrawer();
			setTotals();
		}
		catch (Exception e )
		{
			ADialog.warn(0, this, Msg.getMsg(Env.getCtx(), "Payment processing failed") + ": " + e.getMessage());
		}
	}  // processPayment

	private PosBasePanel p_posPanel;
	private MPOS p_pos;
	private Properties p_ctx;
	private PosOrderModel p_order;
	private CTextField fTotal = new CTextField(10);
	private CTextField fBalance = new CTextField(10);
	private CComboBox tenderTypePick = new CComboBox();
	private PosTextField fPayAmt;
	private CButton f_bProcess;
	private boolean paid = false;
	private BigDecimal balance = Env.ZERO;
	private PosTextField fCheckAccountNo;
	private PosTextField fCheckNo;
	private PosTextField fCheckRouteNo;
	private PosTextField fCCardNo;
	private PosTextField fCCardName;
	private CComboBox fCCardType;

	private CComboBox fCreditNotes;
	private PosTextField fCCardMonth;
	private PosTextField fCCardVC;

	private CLabel lCheckNo;
	private CLabel lCheckAccountNo;
	private CLabel lCheckRouteNo;
	private CLabel lCCardNo;
	private CLabel lCCardName;
	private CLabel lCCardType;
	private CLabel lCreditNotes;
	private CLabel lCCardMonth;
	private CLabel lCCardVC;
	private PosTextField fTenderAmt;
	private CLabel lTenderAmt;
	private PosTextField fReturnAmt;
	private CLabel lReturnAmt;
	private CButton f_bCancel;
	
	private MInvoice creditNote = null;

	public PosPayment(PosBasePanel posPanel) {
		super(Env.getFrame(posPanel),true);
		p_posPanel = posPanel;
		p_pos = posPanel.p_pos;
		p_ctx = p_pos.getCtx();
		p_order = p_posPanel.m_order;
		
		if ( p_order == null )
			dispose();
		
		init();
		pack();
		setLocationByPlatform(true);
	}

	private void init() {
		
		Font font = AdempierePLAF.getFont_Field().deriveFont(18f);
		
		//	North
		CPanel mainPanel = new CPanel(new MigLayout("hidemode 3", 
				"[100:100:300, trailing]20[200:200:300,grow, trailing]"));
		getContentPane().add(mainPanel);
		
		mainPanel.setBorder(new TitledBorder(Msg.translate(p_ctx, "Payment")));
		CLabel gtLabel = new CLabel(Msg.translate(p_ctx, "GrandTotal"));
		mainPanel.add(gtLabel, "growx");
		mainPanel.add(fTotal, "wrap, growx");
		fTotal.setEditable(false);
		fTotal.setFont(font);
		fTotal.setHorizontalAlignment(JTextField.TRAILING);
		
		mainPanel.add(new CLabel(Msg.translate(p_ctx, "Balance")), "growx");
		mainPanel.add(fBalance, "wrap, growx");
		fBalance.setEditable(false);
		fBalance.setFont(font);
		fBalance.setHorizontalAlignment(JTextField.TRAILING);
		
		
		mainPanel.add(new CLabel(Msg.translate(p_ctx, "TenderType"), "growx"));
		// Payment type selection
		int AD_Column_ID = 8416; //C_Payment_v.TenderType
		MLookup lookup = MLookupFactory.get(Env.getCtx(), 0, 0, AD_Column_ID, DisplayType.List);
		ArrayList<Object> types = lookup.getData(true, false, true, true);
		ValueNamePair vnp = new ValueNamePair("N", "A Crédito");
		types.add(vnp);
		vnp = new ValueNamePair("F", "Nota de Crédito");
		types.add(vnp);
		DefaultComboBoxModel typeModel = new DefaultComboBoxModel(types.toArray()); 
		tenderTypePick.setModel(typeModel);
		// default to cash payment
		for (Object obj : types)
		{
			if ( obj instanceof ValueNamePair )
			{
				ValueNamePair key = (ValueNamePair) obj;
				if ( key.getID().equals("X"))   // Cash
					tenderTypePick.setSelectedItem(key);
				
				if ( ! "CKXNF".contains(key.getID() ) )
					tenderTypePick.removeItem(key);
			}
		}
		
		tenderTypePick.setFont(font);
		tenderTypePick.addActionListener(this);
		tenderTypePick.setRenderer(new ListCellRenderer() {
			protected DefaultListCellRenderer defaultRenderer = new DefaultListCellRenderer();

			public Component getListCellRendererComponent(JList list, Object value,
					int index, boolean isSelected, boolean cellHasFocus) {
				
				JLabel renderer = (JLabel) defaultRenderer
		        .getListCellRendererComponent(list, value, index, isSelected,
		            cellHasFocus);
				
				renderer.setPreferredSize(new Dimension(50, 50));
				renderer.setHorizontalAlignment(JLabel.CENTER);
				
				return renderer;

			}
		});
		
		mainPanel.add(tenderTypePick, "wrap, h 50!, growx");
		
		fPayAmt = new PosTextField(Msg.translate(p_ctx, "PayAmt"), p_posPanel, p_pos.get_ValueAsInt("OSK_KeyLayout_ID"),  DisplayType.getNumberFormat(DisplayType.Amount));
		mainPanel.add(new CLabel(Msg.translate(p_ctx, "PayAmt")), "growx");
		fPayAmt.setFont(font);
		fPayAmt.setHorizontalAlignment(JTextField.TRAILING);
		fPayAmt.addActionListener(this);
		mainPanel.add(fPayAmt, "wrap, growx");
		
		fTenderAmt = new PosTextField(Msg.translate(p_ctx, "AmountTendered"), p_posPanel, p_pos.get_ValueAsInt("OSNP_KeyLayout_ID"),  DisplayType.getNumberFormat(DisplayType.Amount));
		lTenderAmt = new CLabel(Msg.translate(p_ctx, "AmountTendered"));
		mainPanel.add(lTenderAmt, "growx");
		fTenderAmt.addActionListener(this);
		fTenderAmt.setFont(font);
		fTenderAmt.setHorizontalAlignment(JTextField.TRAILING);
		mainPanel.add(fTenderAmt, "wrap, growx");
		
		fReturnAmt = new PosTextField(Msg.translate(p_ctx, "AmountReturned"), p_posPanel, p_pos.get_ValueAsInt("OSNP_KeyLayout_ID"),  DisplayType.getNumberFormat(DisplayType.Amount));
		lReturnAmt = new CLabel(Msg.translate(p_ctx, "AmountReturned"));
		mainPanel.add(lReturnAmt, "growx");
		fReturnAmt.setFont(font);
		fReturnAmt.setHorizontalAlignment(JTextField.TRAILING);
		mainPanel.add(fReturnAmt, "wrap, growx");
		fReturnAmt.setEditable(false);
		
		fCheckRouteNo = new PosTextField(Msg.translate(p_ctx, "RoutingNo"), p_posPanel,p_pos.get_ValueAsInt("OSK_KeyLayout_ID"),  new DecimalFormat("#"));
		lCheckRouteNo = new CLabel(Msg.translate(p_ctx, "RoutingNo"));
		mainPanel.add(lCheckRouteNo, "growx");
		mainPanel.add(fCheckRouteNo, "wrap, growx");
		fCheckRouteNo.setFont(font);
		fCheckRouteNo.setHorizontalAlignment(JTextField.TRAILING);
			
		fCheckAccountNo = new PosTextField(Msg.translate(p_ctx, "AccountNo"), p_posPanel, p_pos.get_ValueAsInt("OSNP_KeyLayout_ID"),  new DecimalFormat("#"));
		lCheckAccountNo = new CLabel(Msg.translate(p_ctx, "AccountNo"));
		mainPanel.add(lCheckAccountNo, "growx");
		mainPanel.add(fCheckAccountNo, "wrap, growx");
		fCheckAccountNo.setFont(font);
		fCheckAccountNo.setHorizontalAlignment(JTextField.TRAILING);
		
		fCheckNo = new PosTextField(Msg.translate(p_ctx, "CheckNo"), p_posPanel, p_pos.get_ValueAsInt("OSNP_KeyLayout_ID"),  new DecimalFormat("#"));
		lCheckNo = new CLabel(Msg.translate(p_ctx, "CheckNo"));
		mainPanel.add(lCheckNo, "growx");
		mainPanel.add(fCheckNo, "wrap, growx");
		fCheckNo.setFont(font);
		fCheckNo.setHorizontalAlignment(JTextField.TRAILING);
		
		/**
		 *	Load Credit Cards
		 */
		ValueNamePair[] ccs = p_order.getCreditCards((BigDecimal) fPayAmt.getValue());
		//	Set Selection
		fCCardType = new CComboBox(ccs);
		fCCardType.setRenderer(new ListCellRenderer() {
			protected DefaultListCellRenderer defaultRenderer = new DefaultListCellRenderer();

			public Component getListCellRendererComponent(JList list, Object value,
					int index, boolean isSelected, boolean cellHasFocus) {
				
				JLabel renderer = (JLabel) defaultRenderer
		        .getListCellRendererComponent(list, value, index, isSelected,
		            cellHasFocus);
				
				renderer.setPreferredSize(new Dimension(50, 50));
				renderer.setHorizontalAlignment(JLabel.CENTER);
				
				return renderer;

			}
		});
		lCCardType = new CLabel(Msg.translate(p_ctx, "CreditCardType"));
		mainPanel.add(lCCardType, "growx");
		mainPanel.add(fCCardType, "h 50, wrap, growx");
		fCCardType.setFont(font);
			
		fCCardNo = new PosTextField(Msg.translate(p_ctx, "CreditCardNumber"), p_posPanel, p_pos.get_ValueAsInt("OSNP_KeyLayout_ID"),  new DecimalFormat("#"));
		lCCardNo = new CLabel(Msg.translate(p_ctx, "CreditCardNumber"));
		mainPanel.add(lCCardNo, "growx");
		mainPanel.add(fCCardNo, "wrap, growx");
		fCCardNo.setFont(font);
		fCCardNo.setHorizontalAlignment(JTextField.TRAILING);
		
		fCCardName = new PosTextField(Msg.translate(p_ctx, "Name"), p_posPanel, p_pos.get_ValueAsInt("OSK_KeyLayout_ID"));
		lCCardName = new CLabel(Msg.translate(p_ctx, "Name"));
		mainPanel.add(lCCardName, "growx");
		mainPanel.add(fCCardName, "wrap, growx");
		fCCardName.setFont(font);
		fCCardName.setHorizontalAlignment(JTextField.TRAILING);
		
		fCCardMonth = new PosTextField(Msg.translate(p_ctx, "Expires"), p_posPanel, p_pos.get_ValueAsInt("OSNP_KeyLayout_ID"), new DecimalFormat("#"));
		lCCardMonth = new CLabel(Msg.translate(p_ctx, "Expires"));
		mainPanel.add(lCCardMonth, "growx");
		mainPanel.add(fCCardMonth, "wrap, w 75!");
		fCCardMonth.setFont(font);
		fCCardMonth.setHorizontalAlignment(JTextField.TRAILING);
		
		fCCardVC = new PosTextField(Msg.translate(p_ctx, "CVC"), p_posPanel, p_pos.get_ValueAsInt("OSNP_KeyLayout_ID"),  new DecimalFormat("#"));
		lCCardVC = new CLabel(Msg.translate(p_ctx, "CVC"));
		mainPanel.add(lCCardVC, "growx");
		mainPanel.add(fCCardVC, "wrap, w 75!");
		fCCardVC.setFont(font);
		fCCardVC.setHorizontalAlignment(JTextField.TRAILING);
		//SHW Credit Notes

		/**
		 *	Load Credit Notes
		 */
		ValueNamePair[] cnp = p_order.getCreditNotes();
		//	Set Selection
		fCreditNotes = new CComboBox(cnp);
		fCreditNotes.setRenderer(new ListCellRenderer() {
			protected DefaultListCellRenderer defaultRenderer = new DefaultListCellRenderer();

			public Component getListCellRendererComponent(JList list, Object value,
					int index, boolean isSelected, boolean cellHasFocus) {
				
				JLabel renderer = (JLabel) defaultRenderer
		        .getListCellRendererComponent(list, value, index, isSelected,
		            cellHasFocus);
				
				renderer.setPreferredSize(new Dimension(50, 50));
				renderer.setHorizontalAlignment(JLabel.CENTER);
				
				return renderer;

			}
		});
		lCreditNotes = new CLabel(Msg.translate(p_ctx, "CreditNote"));
		fCreditNotes.addActionListener(this);
		mainPanel.add(lCreditNotes, "growx");
		mainPanel.add(fCreditNotes, "h 50, wrap, growx");
		fCCardType.setFont(font);
		//SHW Ende
		
		AppsAction actCancel = new AppsAction("Cancel", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), false);
		actCancel.setDelegate(this);
		f_bCancel = (CButton)actCancel.getButton();
		f_bCancel.setFocusable(false);
		mainPanel.add (f_bCancel, "h 50!, w 50!, skip, split 2, trailing");
		
		AppsAction act = new AppsAction("Ok", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), false);
		act.setDelegate(this);
		f_bProcess = (CButton)act.getButton();
		f_bProcess.setFocusable(false);
		mainPanel.add (f_bProcess, "h 50!, w 50!");
		
		pack();
		
		setTotals();
	} // init

	/**
	 * Set totals for panel and visibility of components
	 * 
	 */
	private void setTotals() {

		String tenderType = ((ValueNamePair) tenderTypePick.getValue()).getID();
		boolean cash = MPayment.TENDERTYPE_Cash.equals(tenderType);
		boolean check = MPayment.TENDERTYPE_Check.equals(tenderType);
		boolean creditcard = MPayment.TENDERTYPE_CreditCard.equals(tenderType);
		boolean account = MPayment.TENDERTYPE_Account.equals(tenderType);
		boolean creditNote = tenderType.equals("F");
		boolean returnVisible = creditNote || cash;
		fTenderAmt.setValue(Env.ZERO);
		fTenderAmt.setVisible(cash);
		fReturnAmt.setVisible(returnVisible);
		lTenderAmt.setVisible(cash);
		lReturnAmt.setVisible(returnVisible);
		
		fCheckAccountNo.setVisible(check);
		fCheckNo.setVisible(check);
		fCheckRouteNo.setVisible(check);
		lCheckAccountNo.setVisible(check);
		lCheckNo.setVisible(check);
		lCheckRouteNo.setVisible(check);

		fCCardMonth.setVisible(creditcard);
		fCCardName.setVisible(creditcard);
		fCCardNo.setVisible(creditcard);
		fCCardType.setVisible(creditcard);
		fCCardVC.setVisible(creditcard);
		lCCardMonth.setVisible(creditcard);
		lCCardName.setVisible(creditcard);
		lCCardNo.setVisible(creditcard);
		lCCardType.setVisible(creditcard);
		lCCardVC.setVisible(creditcard);
		
		lCreditNotes.setVisible(creditNote);
		fCreditNotes.setVisible(creditNote);

		fTotal.setValue(p_order.getGrandTotal());
		
		BigDecimal received = p_order.getPaidAmt();		
		balance  = p_order.getGrandTotal().subtract(received);
		balance = balance.setScale(MCurrency.getStdPrecision(p_ctx, p_order.getC_Currency_ID()));
		if ( balance.compareTo(Env.ZERO) <= 0 )
		{
			paid = true;
			
			if ( balance.compareTo(Env.ZERO) < 0 )
					ADialog.warn(0, this, Msg.getMsg(p_ctx, "Change") + ": " + balance);
			dispose();
		}
		
		fBalance.setValue(balance);
		fPayAmt.setValue(balance);
		if ( !MPayment.TENDERTYPE_Cash.equals(tenderType) )
		{
			fPayAmt.requestFocusInWindow();
			SwingUtilities.invokeLater(new Runnable() {

				public void run() {
					fPayAmt.selectAll();
				}
			});
		}
		else
		{
			fTenderAmt.requestFocusInWindow();
		}
		
		pack();
	} // setTotals


	/**
	 * Get key value
	 * 
	 */
	public void keyReturned(MPOSKey key) {
		
		String text = key.getText();
		String payAmt = fPayAmt.getText();
		String selected = fPayAmt.getSelectedText();
		if ( selected != null && !selected.isEmpty() )
		{
			payAmt = payAmt.replaceAll(selected, "");
		}
		
		if ( text != null && !text.isEmpty() )
		{
			if ( text.equals(".") && payAmt.indexOf(".") == -1 )
			{
				fPayAmt.setText(payAmt + text);
				return;
			}
			try
			{
				Integer.parseInt(text);		// test if number
				fPayAmt.setText(payAmt + text);
			}
			catch (NumberFormatException e)
			{
				// ignore non-numbers
			}
		}
	} // keyReturned


	/**
	 * Display and initialize Payment dialog
	 * 
	 */
	public static boolean pay(I_POS posPanel) {
		
		PosPayment pay = new PosPayment(posPanel);
		pay.setVisible(true);
		
		return pay.isPaid();
	}  // pay

	private boolean isPaid() {
		return paid ;
	}

	public void vetoableChange(PropertyChangeEvent arg0)
			throws PropertyVetoException {
		// TODO Auto-generated method stub
		
	}
	
	/**
	 * Get BigDecimal format
	 * @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com 03/02/2014, 21:53:00
	 * @param value
	 * @param properties
	 * @return
	 * @return BigDecimal
	 */
	public BigDecimal getNumber(Properties properties, String value, int displayType) {
		try {
			if(value != null 
					&& value.length() > 0) {
				DecimalFormat dFormat = DisplayType.getNumberFormat(displayType);
				//	
				dFormat.setParseBigDecimal(true);
				BigDecimal parsed = (BigDecimal)dFormat.parse(value, new ParsePosition(0));
				return parsed;
			}
		} catch (Exception e) {
		}
		//	
		return Env.ZERO;
	}
	
}
