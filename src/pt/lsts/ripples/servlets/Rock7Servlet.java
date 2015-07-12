package pt.lsts.ripples.servlets;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.annotation.adapters.HexBinaryAdapter;

import pt.lsts.ripples.model.Address;
import pt.lsts.ripples.model.HubIridiumMsg;
import pt.lsts.ripples.model.HubSystem;
import pt.lsts.ripples.model.Store;
import pt.lsts.ripples.model.iridium.IridiumMessage;
import pt.lsts.ripples.util.IridiumUtils;

import com.firebase.client.utilities.Pair;

public class Rock7Servlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static final HexBinaryAdapter hexAdapter = new HexBinaryAdapter();
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat(
			"yy-MM-dd HH:mm:ss");
	static {
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		String data = req.getParameter("data");
		String imei = req.getParameter("imei");
		String transmit_time = req.getParameter("transmit_time");
		try {
			byte[] dataArr = hexAdapter.unmarshal(data);
			IridiumMessage msg = IridiumMessage.deserialize(dataArr);

			if (msg != null) {
				HubIridiumMsg m = new HubIridiumMsg();
				m.setMsg(data);
				m.setType(msg.getMessageType());
				m.setCreated_at(dateFormat.parse(transmit_time));
				m.setUpdated_at(new Date());
				Store.ofy().save().entity(m);

				Logger.getLogger(getClass().getName()).log(Level.INFO, "Received message from RockBlock");
				
				IridiumMsgHandler.setMessage(imei, msg);
				
				HubSystem system = Store.ofy().load().type(HubSystem.class)
						.id(msg.getSource()).now();
				Address addr = Store.ofy().load().type(Address.class).id(msg.getSource()).now();
				
				if (system != null && !imei.equals(system.getIridium())) {
					system.setIridium(imei);
					Store.ofy().save().entity(system);
				}
				
				if (addr != null && !imei.equals(addr.imei)) {
					addr.imei = imei;
					Store.ofy().save().entity(addr);
				}
				
				String destImei = IridiumUtils.getIMEI(msg.destination);
				if (msg.destination != 65535 && destImei != null) {
					Logger.getLogger(getClass().getName()).log(Level.INFO, " Forwarding "+msg.getClass().getSimpleName()+" to "+destImei);
					Pair<Integer, String> res = IridiumUtils.sendviaRockBlock(destImei, msg.serialize());
					if (res.getFirst() > 299) {
						Logger.getLogger(getClass().getName()).log(Level.WARNING, "Error "+res.getFirst()+" forwarding "+msg.getClass().getSimpleName()+" to "+msg.getDestination()+": "+res.getSecond());
					resp.setStatus(res.getFirst());
					}
					resp.getWriter().write(res.getSecond());
					resp.getWriter().close();
					return;
				}
				else {
					IridiumUpdatesServlet.sendToSubscribers(msg);
				}
			}
			else
				Logger.getLogger(getClass().getName()).log(Level.INFO, "Received empty message from RockBlock");
		} catch (Exception e) {
			e.printStackTrace();
		}

		resp.getWriter().write("200 OK");
		resp.setStatus(200);
		resp.getWriter().close();
	}
}
