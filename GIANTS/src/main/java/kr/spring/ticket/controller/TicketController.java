package kr.spring.ticket.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import kr.spring.member.vo.MemberVO;
import kr.spring.ticket.service.TicketService;
import kr.spring.ticket.vo.GameVO;
import kr.spring.ticket.vo.GradeVO;
import kr.spring.ticket.vo.SeatVO;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class TicketController {
	@Autowired
	private TicketService ticketService;
	
	// javaBean(VO) 초기화
	@ModelAttribute
	public GameVO initGame() { return new GameVO(); }
	
	/* ----- [Ticket] 메인(요금안내 및 티켓예매 버튼 활성화) -----*/
	@RequestMapping("/ticket/ticketInfo.do")
	public String ticketInfo() { return "ticketInfo"; }
	
	/* ----- [Game] 경기목록 -----*/
	@RequestMapping("/ticket/gameList.do")
	public String gameList(GameVO gameVO, Model model) {
		int count = ticketService.selectRowCount(gameVO);
		
		List<GameVO> list = ticketService.selectTicketGameList(gameVO);
		
		model.addAttribute("count", count);
		model.addAttribute("list", list);
		
		return "gameList";
	}
	
	/* ----- [Ticket] 예매메인 -----*/
	@RequestMapping("/ticket/ticketMain.do")
	public String ticketMain(GradeVO gradeVO, Model model) {
		List<GradeVO> list = ticketService.selectGradeList(gradeVO);
		
		model.addAttribute("list", list);
		
		return "ticketMain";
	}
}