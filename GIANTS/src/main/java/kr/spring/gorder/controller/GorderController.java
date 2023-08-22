package kr.spring.gorder.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.siot.IamportRestClient.IamportClient;

import kr.spring.gcart.service.GcartService;
import kr.spring.gcart.vo.GcartVO;
import kr.spring.goods.service.GoodsService;
import kr.spring.goods.vo.GoodsOptionVO;
import kr.spring.goods.vo.GoodsVO;
import kr.spring.gorder.service.GorderService;
import kr.spring.gorder.vo.GorderDetailVO;
import kr.spring.gorder.vo.GorderVO;
import kr.spring.member.service.MemberService;
import kr.spring.member.vo.MemberDetailVO;
import kr.spring.member.vo.MemberVO;
import kr.spring.util.PagingUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class GorderController {
	@Autowired
	private GorderService orderService;
	@Autowired
	private GcartService cartService;
	@Autowired
	private GoodsService goodsService;
	@Autowired
	private MemberService memberService;
	//자바빈 초기화
	@ModelAttribute("orderVO")
	public GorderVO initCommand() {
		return new GorderVO();
	}

	private IamportClient api;

	// 토큰 발급
	public GorderController() {
		this.api = new IamportClient("0217183128333403",
				"8l2vQIsYUIxQ8MubQCY7fe5vQUbLZAaf8b5jWmYwWwCJDClZ0bwyM0EylaF5ALeGAtKYuYnFxV8zt3Ga");
	}

	/*
	 * ==================== 상품 구매 ====================
	 */

	// ==============================바로구매 시작 (goodsView > 바로구매)==================================================
	//장바구니 임의등록(장바구니 목록을 보여주진 않고 등록만 해주고 바로 결제처리를 해준다)
		@RequestMapping("/cart/write.do")
		@ResponseBody
		public Map<String, String> addToCart(GcartVO cartVO, HttpSession session) {
			Map<String, String> mapJson = new HashMap<String, String>();
			MemberVO user = (MemberVO) session.getAttribute("user");
			// 로그인 x
			if (user == null) {
				mapJson.put("result", "logout");
			}
			// 로그인 o
			else {
				cartVO.setMem_num(user.getMem_num()); 
				GcartVO db_cart = cartService.getCart(cartVO);
				
				log.debug("GcartVO : " + db_cart);
				
				// 재고 확인
				// 재고를 구하기 위해 상품 정보 호출
				GoodsVO db_goods = goodsService.selectGoodsAllInfo(db_cart.getGoods_num());
				// 굿즈 재고, 구매수량
				int db_stock = cartService.getStockByoption(db_goods.getGoods_num(), cartVO.getOpt_num());
				int order_quantity = cartVO.getOrder_quantity();
				log.debug("<<D 굿즈 재고 >> : " + db_stock);
				log.debug("<<D 구매 수량 >> : " + order_quantity);
				
				if(db_stock < order_quantity) {
					mapJson.put("result", "overquantity"); //재고가 구매하려는 수량보다 적음
				}
				else {
				cartService.insertCart(cartVO);
				mapJson.put("result", "success");
				}
			}

			return mapJson;
		}
	
	
	@RequestMapping("/gorder/directBuy.do")
	@ResponseBody
	public Map<String, String> directBuy(GorderVO orderVO, HttpSession session) { // 상품번호, 주문수, 옵션(처리 아직) 가져옴
		Map<String, String> mapJson = new HashMap<String, String>();
		MemberVO user = (MemberVO) session.getAttribute("user");
		// 로그인 x
		if (user == null) {
			mapJson.put("result", "logout");
		}
		// 로그인 o
		else {
			orderVO.setMem_num(user.getMem_num()); // 현재 로그인된 회원의 mem_num 설정
			mapJson.put("result", "success"); // success인 경우 orderForm 호출
		}

		return mapJson;
	}

	// 바로 구매 폼
	//order_quantity(detail, goods_dprice, goods_num, opt_num(vo에만 있음) 등을 orderVO에 담아서 올 것임
	@PostMapping("/gorder/orderFormDirect.do")
	public String directForm(@ModelAttribute("orderVO") GorderVO orderVO, HttpSession session, Model model,
			HttpServletRequest request) {
		// log.debug("<<cart_numbers>> : " + orderVO.getCart_numbers());
		log.debug("<<바로구매 orderVO>> : " + orderVO);

		MemberVO user = (MemberVO) session.getAttribute("user");
		
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("mem_num", user.getMem_num());
		
		int all_total = cartService.getTotalByMem_num(map);
		if (all_total <= 0) {
			model.addAttribute("message", "정상적인 주문이 아니거나 상품의 수량이 부족합니다.");
			model.addAttribute("url", request.getContextPath() + "/gorder/goods_cart.do");
			return "common/resultView";
		}

		// 장바구니에 담겨있는 상품 정보 호출
		List<GcartVO> cartList = cartService.getListCart(map);

		for (GcartVO cart : cartList) {
			GoodsVO goods = goodsService.selectGoods(cart.getGoods_num());
			if (goods.getGoods_status() == 2) {
				// 상품 미표시
				model.addAttribute("message", "[" + goods.getGoods_name() + "]상품판매 중지");
				model.addAttribute("url", request.getContextPath() + "/gorder/goods_cart.do");
				return "common/resultView";
			}

			// 굿즈의 옵션 별 재고 가져오기
			int db_stock = cartService.getStockByoption(cart.getGoods_num(), cart.getOpt_num());
			if (db_stock < cart.getOrder_quantity()) {
				// 상품 재고 수량 부족
				model.addAttribute("message", "[" + goods.getGoods_name() + "]재고수량 부족으로 주문 불가");
				model.addAttribute("url", request.getContextPath() + "/gorder/goods_cart.do");
				return "common/resultView";
			}

			log.debug("<<굿즈 옵션별 재고 db_stock >> : " + db_stock);

		}

		// mem_point 가져오기
		MemberVO memberVO = memberService.selectMember(user.getMem_num());
		int mem_point = memberVO.getMemberDetailVO().getMem_point();

		log.debug("<<장바구니 mem_point >> : " + mem_point);

		model.addAttribute("mem_point", mem_point);
		model.addAttribute("list", cartList);
		model.addAttribute("all_total", all_total);

		return "orderForm";
	}
	
	//=============================================================================================
	//==============================
	// 상품 구매 폼 호출 - 장바구니 > 구매
	@PostMapping("/gorder/orderForm.do")
	public String form(@ModelAttribute("orderVO") GorderVO orderVO, HttpSession session, Model model,
			HttpServletRequest request) {
		// log.debug("<<cart_numbers>> : " + orderVO.getCart_numbers());
		log.debug("<<장바구니 > 구매 >> : " + orderVO);

		if (orderVO.getCart_numbers() == null || orderVO.getCart_numbers().length == 0) {
			model.addAttribute("message", "정상적인 주문이 아닙니다.");
			model.addAttribute("url", request.getContextPath() + "/gorder/goods_cart.do");
			return "common/resultView";
		}

		MemberVO user = (MemberVO) session.getAttribute("user");
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("mem_num", user.getMem_num());
		map.put("cart_numbers", orderVO.getCart_numbers()); // 여기서 처리가 안되는듯?
		int all_total = cartService.getTotalByMem_num(map);
		if (all_total <= 0) {
			model.addAttribute("message", "정상적인 주문이 아니거나 상품의 수량이 부족합니다.");
			model.addAttribute("url", request.getContextPath() + "/gorder/goods_cart.do");
			return "common/resultView";
		}

		// 장바구니에 담겨있는 상품 정보 호출
		List<GcartVO> cartList = cartService.getListCart(map);

		for (GcartVO cart : cartList) {
			GoodsVO goods = goodsService.selectGoods(cart.getGoods_num());
			if (goods.getGoods_status() == 2) {
				// 상품 미표시
				model.addAttribute("message", "[" + goods.getGoods_name() + "]상품판매 중지");
				model.addAttribute("url", request.getContextPath() + "/gorder/goods_cart.do");
				return "common/resultView";
			}

			// 굿즈의 옵션 별 재고 가져오기
			int db_stock = cartService.getStockByoption(cart.getGoods_num(), cart.getOpt_num());
			if (db_stock < cart.getOrder_quantity()) {
				// 상품 재고 수량 부족
				model.addAttribute("message", "[" + goods.getGoods_name() + "]재고수량 부족으로 주문 불가");
				model.addAttribute("url", request.getContextPath() + "/gorder/goods_cart.do");
				return "common/resultView";
			}

			log.debug("<<굿즈 옵션별 재고 db_stock >> : " + db_stock);

		}

		// mem_point 가져오기
		MemberVO memberVO = memberService.selectMember(user.getMem_num());
		int mem_point = memberVO.getMemberDetailVO().getMem_point();

		log.debug("<<장바구니 mem_point >> : " + mem_point);

		model.addAttribute("mem_point", mem_point);
		model.addAttribute("list", cartList);
		model.addAttribute("all_total", all_total);

		return "orderForm";
	}

	/* 결제 api 작업 */
	@RequestMapping(value = "/gorder/insertMPay.do", method = RequestMethod.POST)
	@ResponseBody
	public Map<String, String> insertMPay(GorderVO orderVO, GorderDetailVO orderDetailVO, HttpSession session,
			RedirectAttributes rttr, Model model, HttpServletRequest request) {
		log.debug("<<결제 api orderVO>> : " + orderVO);
		log.debug("<<결제 api orderDetailVO>> : " + orderDetailVO); //제대로 다 읽어옴!!

		MemberVO user = (MemberVO) session.getAttribute("user");
		Map<String, String> mapJson = new HashMap<String, String>();

		// 로그인 x
		if (user == null) {
			mapJson.put("result", "logout");
		}
		// 로그인 o
		orderVO.setMem_num(user.getMem_num()); // 현재 로그인된 회원의 mem_num 설정

		Map<String, Object> map = new HashMap<String, Object>();
		map.put("mem_num", user.getMem_num());
		map.put("cart_numbers", orderVO.getCart_numbers());
		int all_total = cartService.getTotalByMem_num(map);

		if (all_total <= 0) {
			mapJson.put("result", "orderError");
		}

		// 장바구니에 담겨있는 상품 정보 호출
		List<GcartVO> cartList = cartService.getListCart(map);
		List<GorderDetailVO> orderDetailList = new ArrayList<GorderDetailVO>();
		// ========
		for (GcartVO cart : cartList) {
			GoodsVO goods = goodsService.selectGoods(cart.getGoods_num());
			if (goods.getGoods_status() == 2) {
				// 상품 미표시
				mapJson.put("result", "noStatus");
			}

			// 굿즈의 옵션 별 재고 가져오기

			int db_stock = cartService.getStockByoption(cart.getGoods_num(), cart.getOpt_num());
			if (db_stock < cart.getOrder_quantity()) {
				// 상품 재고 수량 부족
				mapJson.put("result", "noQuantity");
			}
			log.debug("<<주문api 굿즈 옵션 별 재고 : >> " + db_stock);
			log.debug("<<주문api 주문한 재고 : >> " + cart.getOrder_quantity());

			
			 GorderDetailVO orderDetail = new GorderDetailVO();
			 
			 orderDetail.setMem_num(user.getMem_num());
			 orderDetail.setGoods_num(cart.getGoods_num());
			 orderDetail.setGoods_name(cart.getGoodsVO().getGoods_name());
			 orderDetail.setGoods_dprice(cart.getGoodsVO().getGoods_dprice());
			 orderDetail.setGoods_size(cart.getGoods_size());
			 orderDetail.setOrder_quantity(cart.getOrder_quantity());
			 orderDetail.setGoods_total(cart.getSub_total());
			 orderDetail.setOrder_point(cart.getOrder_point()); //적립된 포인트
			 //orderDetail.setUsed_point(orderDetailVO.getUsed_point()); //사용한 포인트 -orderVO
			 
			  log.debug("<<orderDetail 세팅>> : " + orderDetail);
			  
			  orderDetailList.add(orderDetail);
			 
		}
		// =============================================
		// 포인트를 사용한 경우 포인트 차감시키기 - used_point > 0
		if (orderVO.getUsed_point() > 0) {
			orderService.usingPoint(orderVO.getUsed_point(), user.getMem_num());
		}

		// 주문 상품의 대표 상품명 생성
		String goods_name = "";
		if (cartList.size() == 1) {
			goods_name = cartList.get(0).getGoodsVO().getGoods_name();
			log.debug("<< 굿즈 하나 산 경우 이름 >>: " + goods_name);
		} else {
			goods_name = cartList.get(0).getGoodsVO().getGoods_name() + "외 " + (cartList.size() - 1) + "건";
			log.debug("<< 굿즈 외 1건 이런 식으로 됐는지 확인 >>: " + goods_name);

		}

		orderVO.setGoods_name(goods_name);
		orderVO.setOrder_total(all_total);
		orderVO.setMem_num(user.getMem_num());

		// 성공적으로 수행된 경우 주문 등록 + 굿즈 옵션 별 수량 차감
		orderService.insertOrder(orderVO, orderDetailList);
		
		
		// =============================================
		// 예상 포인트 mem_point에 적립해주기
		orderService.updatePoint(user.getMem_num());
		// =============================================

		mapJson.put("result", "success");

		return mapJson; 
	}

	/*
	 * ==================== 회원 포인트 읽어오기 ====================
	 */

	@RequestMapping("/gorder/getMemberPoint.do")
	@ResponseBody
	public Map<String, Object> formPoint(HttpSession session) {
		Map<String, Object> mapJson = new HashMap<>();
		MemberVO user = (MemberVO) session.getAttribute("user");
		if (user == null) {
			mapJson.put("result", "logout");
		} else {
			MemberVO db_memberDetail = memberService.selectMember(user.getMem_num());
			mapJson.put("result", "success");
			mapJson.put("mem_point", db_memberDetail.getMemberDetailVO().getMem_point());
			log.debug("<<회원 보유 포인트 >> : " + db_memberDetail.getMemberDetailVO().getMem_point());
		}
		return mapJson;
	}

	/*
	 * ==================== 회원 포인트 사용하기 ====================
	 */
	@RequestMapping("/gorder/usingMemberPoint.do")
	@ResponseBody
	public Map<String, Object> usingPoint(HttpSession sesssion, @RequestParam int allTotal, @RequestParam int usedPoint) {
		Map<String, Object> mapJson = new HashMap<>();
		MemberVO user = (MemberVO) sesssion.getAttribute("user");
		MemberVO db_memberDetail = memberService.selectMember(user.getMem_num());
		int mem_point = db_memberDetail.getMemberDetailVO().getMem_point();
		// dead code, 굳이 쓸 필요가 없음
		/*
		 * if(user == null) { mapJson.put("result", "logout"); }
		 */
		log.debug("<<allTotal>> : " + allTotal);
		log.debug("<<usedPoint>> : " + usedPoint);
		
		if (mem_point <= 0) {
			mapJson.put("result", "littlePoint");
		} 
		//포인트 입력을 음수로 한 경우
		else if(usedPoint<0) {
			mapJson.put("result", "underPoint");
		}
		//회원의 포인트가 결제 금액보다 큰 경우
		else if(usedPoint > allTotal) {
			mapJson.put("result", "overPoint");
		}
		else {
			mapJson.put("result", "success");
			mapJson.put("mem_point", db_memberDetail.getMemberDetailVO().getMem_point()); //회원 보유 포인트 넘겨주기
		}
		return mapJson;
	}
	//포인트 사용 취소
	@GetMapping("/gorder/cancelMemberPoint.do")
	@ResponseBody
	public Map<String, String> cancelMemberPoint(@RequestParam("usedPoint") int usedPoint, HttpSession sesssion) {
		Map<String, String> mapJson = new HashMap<>();
		MemberVO user = (MemberVO) sesssion.getAttribute("user");
		MemberVO db_memberDetail = memberService.selectMember(user.getMem_num());
		int mem_point = db_memberDetail.getMemberDetailVO().getMem_point();

		if (mem_point < 0) {
			mapJson.put("result", "littlePoint");
		} 
		else {
			mapJson.put("result", "success");
		}
		return mapJson;
	}

	/*
	 * ==================== 회원 주소 읽기(배송지 선택) ====================
	 */
	@RequestMapping("/gorder/getMemberAddress.do")
	@ResponseBody
	public Map<String, Object> formAddress(HttpSession session) {
		Map<String, Object> mapJson = new HashMap<String, Object>();
		MemberVO user = (MemberVO) session.getAttribute("user");
		if (user == null) {
			mapJson.put("result", "logout");
		} else {
			MemberVO db_member = memberService.selectMember(user.getMem_num());
			mapJson.put("result", "success");
			mapJson.put("zipcode", db_member.getMemberDetailVO().getMem_zipcode());
			mapJson.put("address1", db_member.getMemberDetailVO().getMem_address1());
			mapJson.put("address2", db_member.getMemberDetailVO().getMem_address2());
			mapJson.put("phone", db_member.getMemberDetailVO().getMem_phone());
		}

		return mapJson;
	}

	/*
	 * ==================== 주문 목록 - 결제 후 여기로 이동 ====================
	 */
	@RequestMapping("/gorder/orderList.do")
	public ModelAndView orderList(@RequestParam(value = "pageNum", defaultValue = "1") int currentPage, String keyfield,
			String keyword, HttpSession session) {
		MemberVO user = (MemberVO) session.getAttribute("user");

		Map<String, Object> map = new HashMap<String, Object>();
		map.put("keyfield", keyfield);
		map.put("keyword", keyword);
		map.put("mem_num", user.getMem_num());

		// 전체/검색 레코드수
		int count = orderService.selectOrderCountByMem_num(map);

		log.debug("<<count>> : " + count);

		// 페이지 처리
		PagingUtil page = new PagingUtil(keyfield, keyword, currentPage, count, 20, 10, "orderList.do");

		List<GorderVO> list = null;
		if (count > 0) {
			map.put("start", page.getStartRow());
			map.put("end", page.getEndRow());

			list = orderService.selectListOrderByMem_num(map);
		}

		ModelAndView mav = new ModelAndView();
		mav.setViewName("orderList");
		mav.addObject("count", count);
		mav.addObject("list", list);
		mav.addObject("page", page.getPage());

		return mav;
	}

	/*
	 * ==================== 주문상세 - 주문목록에서 입력받음 ====================
	 */
	@RequestMapping("/gorder/orderDetail.do")
	public String formUserDetail(@RequestParam int order_num, Model model) {
		// 주문 정보
		GorderVO order = orderService.selectOrder(order_num);
		
		int all_total = order.getOrder_total();
		model.addAttribute("all_total", all_total);
		
		
		//최종 결제금액 - 포인트
		int order_total = order.getOrder_total() - order.getUsed_point();
		
		
		
		order.setOrder_total(order_total);
		log.debug("<<최종금액 세팅해주기>>" + order.getOrder_total());
		
		// 개별 상품의 주문 정보
		List<GorderDetailVO> detailList = orderService.selectListOrderDetail(order_num);
		log.debug("<<주문상세>> : " + detailList);
		
		model.addAttribute("orderVO", order);
		model.addAttribute("detailList", detailList);

		return "orderDetail";
	}

	/*
	 * ==================== 배송지 변경 ====================
	 */
	// 배송지정보변경 폼 호출
	@GetMapping("/gorder/orderModify.do")
	public String formUserModify(@RequestParam int order_num, Model model) {
		log.debug("<<배송정보 수정 폼 호출 시 order_num>>:" + order_num);
		// 주문 정보
		GorderVO order = orderService.selectOrder(order_num);
		// 개별 상품의 주문 정보
		List<GorderDetailVO> detailList = orderService.selectListOrderDetail(order_num);

		model.addAttribute("orderVO", order);
		model.addAttribute("detailList", detailList);

		return "orderModify";
	}

	// 수정하기- 전송된 데이터 처리
	@PostMapping("/gorder/orderModify.do")
	public String submitUserModify(@Valid GorderVO orderVO, BindingResult result, Model model,
			HttpServletRequest request) {
		log.debug("<<수정하기 GorderVO>> : " + orderVO);

		// 전송된 데이터 유효성 체크 결과 오류가 있으면 폼 호출
		/*
		 * if (result.hasErrors()) { return "orderModify"; }
		 */

		GorderVO db_order = orderService.selectOrder(orderVO.getOrder_num());
		if (db_order.getOrder_status() > 2) {
			// 배송준비중 이상으로 관리자가 변경한 상품을 주문자가 변경할 수 없음
			model.addAttribute("message", "배송상태가 변경되어 주문자가 배송지정보를 변경할 수 없음");
			model.addAttribute("url", request.getContextPath() + "/gorder/orderList.do");
		}
		// 정보 수정
		orderService.updateOrder(orderVO);

		model.addAttribute("message", "배송지정보가 변경되었습니다.");
		model.addAttribute("url",
				request.getContextPath() + "/gorder/orderDetail.do?order_num=" + orderVO.getOrder_num());

		return "common/resultView";
	}

	/*
	 * ==================== 사용자 주문취소 ====================
	 */
	@RequestMapping("/gorder/orderCancel.do")
	public String submitCancel(@RequestParam int order_num, Model model, HttpSession session,
			HttpServletRequest request) {
		GorderVO db_order = orderService.selectOrder(order_num);
		MemberVO user = (MemberVO) session.getAttribute("user");
		if (db_order.getMem_num() != user.getMem_num()) {
			// 타인의 주문을 취소할 수 없음
			model.addAttribute("message", "타인의 주문을 취소할 수 없습니다.");
			model.addAttribute("url", request.getContextPath() + "/order/orderList.do");
			return "common/resultView";
		}

		if (db_order.getOrder_status() > 1) {
			// 배송준비중 이상으로 관리자가 변경한 상품을 주문자가 변경할 수 없음
			model.addAttribute("message", "배송상태가 변경되어 주문자가 주문을 취소할 수 없음");
			model.addAttribute("message", request.getContextPath() + "/order/orderList.do");
			return "common/resultView";
		}

		// 주문취소
		GorderVO vo = new GorderVO();
		vo.setOrder_num(order_num);
		vo.setOrder_status(5);
		orderService.updateOrderStatus(vo);

		model.addAttribute("message", "주문취소가 완료되었습니다.");
		model.addAttribute("url", request.getContextPath() + "/order/orderDetail.do?order_num=" + order_num);

		return "common/resultView";
	}

}
